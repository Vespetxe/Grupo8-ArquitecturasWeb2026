package grupo22.eventocatalogo_service.security;
import org.springframework.stereotype.Component;import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.*;import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;import org.springframework.http.client.*;import org.springframework.web.client.RestClient;
import java.util.*;import java.time.Duration;
import grupo22.eventocatalogo_service.timing.TimedTransport;
@Component
public class ServiceTokens {
 private final AuthorizedClientServiceOAuth2AuthorizedClientManager manager;private final OAuth2AuthorizedClientService cache;
 private final RestClient.Builder builder;
 private final TimedTransport transport;
 private final java.util.Map<String,java.util.concurrent.locks.ReentrantLock> tokenLocks=new java.util.HashMap<>();
 private final java.util.Map<String,io.github.resilience4j.circuitbreaker.CircuitBreaker> tokenCircuits=new java.util.HashMap<>();
 private final io.github.resilience4j.bulkhead.Bulkhead tokenBulkhead;
 private final java.time.Clock clock;
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ServiceTokens.class);
 private final String principal="service:eventos";
 @org.springframework.beans.factory.annotation.Autowired
 public ServiceTokens(Environment e,org.springframework.beans.factory.ObjectProvider<RestClient.Builder> builders){this(e,java.time.Clock.systemUTC(),builders.getObject());}
 public ServiceTokens(Environment e){this(e,java.time.Clock.systemUTC());}
 public ServiceTokens(Environment e,java.time.Clock clock){this(e,clock,RestClient.builder());}
 public ServiceTokens(Environment e,java.time.Clock clock,RestClient.Builder builder){
  this.builder=builder;transport=new TimedTransport(e);this.clock=clock;
  tokenBulkhead=io.github.resilience4j.bulkhead.Bulkhead.of(principal+"-identity",io.github.resilience4j.bulkhead.BulkheadConfig.custom().maxConcurrentCalls(limit(e,"concurrent",2,1,8)).maxWaitDuration(Duration.ZERO).build());
  var registrations=new ArrayList<ClientRegistration>();
  for(String target:List.of("inventario","entradas","compras")){
   String secret=e.getRequiredProperty("TY_SECURITY_"+target.toUpperCase(Locale.ROOT)+"_SECRET");
   if(secret.isBlank()||secret.length()<32)throw new IllegalStateException("Independent technical secret of at least 32 characters required");
   tokenLocks.put(target,new java.util.concurrent.locks.ReentrantLock());
   int window=limit(e,"window",20,2,1000),minimum=limit(e,"minimum",10,1,window);
   var circuit=io.github.resilience4j.circuitbreaker.CircuitBreaker.of(principal+"-oauth-"+target,io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom().slidingWindowSize(window).minimumNumberOfCalls(minimum).failureRateThreshold(limit(e,"failure-percent",50,1,100)).waitDurationInOpenState(Duration.ofMillis(limit(e,"open-ms",10000,1,300000))).permittedNumberOfCallsInHalfOpenState(2).automaticTransitionFromOpenToHalfOpenEnabled(false).clock(clock).ignoreException(f->!tokenFailure(f)).recordException(ServiceTokens::tokenFailure).build());
   circuit.getEventPublisher().onStateTransition(event->tokenLog(target,"OAUTH_CIRCUIT_TRANSITION","NONE",0));
   tokenCircuits.put(target,circuit);
   registrations.add(ClientRegistration.withRegistrationId(target)
   .tokenUri(e.getRequiredProperty("TY_SECURITY_TOKEN_URL")).clientId("eventos-to-"+target)
   .clientSecret(secret)
   .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope(RoutePolicy.requestedScopes("eventos",target)).build());
  }
  var repo=new InMemoryClientRegistrationRepository(registrations);cache=new InMemoryOAuth2AuthorizedClientService(repo);
  manager=new AuthorizedClientServiceOAuth2AuthorizedClientManager(repo,cache);
  var factory=transport.factory(true);
  var responseClient=new org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient();
  responseClient.setRestClient(builder.clone().requestFactory(factory).requestInterceptor(transport.propagate()).requestInterceptor(new grupo22.eventocatalogo_service.observability.CorrelationInterceptor()).messageConverters(c->{c.clear();c.add(new org.springframework.http.converter.FormHttpMessageConverter());c.add(new org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter());})
   .defaultStatusHandler(new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler()).build());
  manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials(c->c.clock(clock).clockSkew(Duration.ofSeconds(30)).accessTokenResponseClient(responseClient)).build());
 }
 private static int limit(Environment env,String key,int fallback,int min,int max){int n=env.getProperty("ticketya.resilience.oauth."+key,Integer.class,fallback);if(n<min||n>max)throw new IllegalStateException("Invalid OAuth resilience configuration");return n;}
 private OAuth2AuthorizedClient usable(String target){
  OAuth2AuthorizedClient client=cache.loadAuthorizedClient(target,principal);
  return client!=null&&client.getAccessToken().getExpiresAt()!=null&&client.getAccessToken().getExpiresAt().isAfter(clock.instant().plusSeconds(30))?client:null;
 }
 private static boolean tokenFailure(Throwable failure){
  boolean transportFailure=false;
  for(int i=0;i<10&&failure!=null;i++,failure=failure.getCause()){
   if(failure instanceof grupo22.eventocatalogo_service.timing.RequestBudget.Exhausted || failure instanceof TechnicalAuthFailure)return false;
   if(failure instanceof org.springframework.web.client.RestClientResponseException response)return response.getStatusCode().value()==429||response.getStatusCode().value()>=500;
   if(failure instanceof OAuth2AuthorizationException auth){String code=auth.getError().getErrorCode();if(java.util.Set.of("invalid_client","invalid_scope","unauthorized_client","invalid_grant","unsupported_grant_type").contains(code))return false;if(java.util.Set.of("server_error","temporarily_unavailable","invalid_token_response").contains(code))transportFailure=true;}
   if(failure instanceof org.springframework.web.client.RestClientException)transportFailure=true;
  }
  return transportFailure;
 }
 public String token(String target){
  var lock=tokenLocks.get(target);if(lock==null)throw new TechnicalAuthFailure();
  var existing=usable(target);if(existing!=null)return existing.getAccessToken().getTokenValue();
  boolean locked=false;long start=System.nanoTime();String category="NONE";
  try{
   locked=lock.tryLock(Math.min(transport.acquireMs,grupo22.eventocatalogo_service.timing.RequestBudget.cap(transport.acquireMs,0)),java.util.concurrent.TimeUnit.MILLISECONDS);
   if(!locked)throw new TechnicalAuthFailure(TechnicalAuthFailure.Reason.TOKEN_LOCK_TIMEOUT);
   existing=usable(target);if(existing!=null)return existing.getAccessToken().getTokenValue();
   grupo22.eventocatalogo_service.timing.RequestBudget.cap(1,2000);
   var client=tokenBulkhead.executeSupplier(()->tokenCircuits.get(target).executeSupplier(()->{var renewed=manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(target).principal(principal).build());if(renewed==null)throw new TechnicalAuthFailure();return renewed;}));
   if(client==null)throw new TechnicalAuthFailure();return client.getAccessToken().getTokenValue();
  }catch(io.github.resilience4j.circuitbreaker.CallNotPermittedException blocked){category="TOKEN_CIRCUIT_OPEN";throw new TechnicalAuthFailure(TechnicalAuthFailure.Reason.TOKEN_CIRCUIT_OPEN);}
   catch(io.github.resilience4j.bulkhead.BulkheadFullException full){category="TOKEN_SATURATED";throw new TechnicalAuthFailure(TechnicalAuthFailure.Reason.TOKEN_SATURATED);}
   catch(InterruptedException interrupted){Thread.currentThread().interrupt();category="TOKEN_INTERRUPTED";throw new TechnicalAuthFailure();}
   catch(TechnicalAuthFailure auth){category=auth.reason().name();throw auth;}
   catch(RuntimeException failure){category=failure instanceof grupo22.eventocatalogo_service.timing.RequestBudget.Exhausted?"TOKEN_BUDGET_EXHAUSTED":"TOKEN_UNAVAILABLE";throw new TechnicalAuthFailure(TechnicalAuthFailure.Reason.TOKEN_UNAVAILABLE);}
  finally{if(locked)lock.unlock();tokenLog(target,"OAUTH_ACQUISITION",category,(System.nanoTime()-start)/1_000_000L);}
 }
 private void tokenLog(String target,String event,String category,long duration){
  var circuit=tokenCircuits.get(target);if(circuit==null)return;
  var entry=LOG.atInfo().addKeyValue("eventType",event).addKeyValue("dependency","identidad").addKeyValue("operation","oauth-token-"+target).addKeyValue("circuit",circuit.getName()).addKeyValue("circuitState",circuit.getState().name()).addKeyValue("durationMs",duration).addKeyValue("errorCategory",category);
  var context=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();if(context instanceof org.springframework.web.context.request.ServletRequestAttributes servlet){var state=(grupo22.eventocatalogo_service.observability.HttpLogState)servlet.getRequest().getAttribute(grupo22.eventocatalogo_service.observability.HttpLogState.ATTRIBUTE);if(state!=null)entry.addKeyValue("correlationId",state.correlationId());}
  entry.log("Technical token acquisition");
 }
 private void invalidate(String target,String used){var lock=tokenLocks.get(target);if(lock!=null&&lock.tryLock())try{OAuth2AuthorizedClient current=cache.loadAuthorizedClient(target,principal);if(current!=null&&current.getAccessToken().getTokenValue().equals(used))cache.removeAuthorizedClient(target,principal);}finally{lock.unlock();}}
 @jakarta.annotation.PreDestroy public void closeTransport()throws java.io.IOException{transport.close();}
 public ClientHttpRequestInterceptor interceptor(String target){return (request,body,execution)->{
  String used=token(target);request.getHeaders().setBearerAuth(used);
  var response=execution.execute(request,body);
  if(response.getStatusCode().value()==401||response.getStatusCode().value()==403){
   invalidate(target,used);response.close();throw new TechnicalAuthFailure();
  }return response;
 };}
 public RestClient client(String target,String baseUrl){
  var factory=transport.factory(false);
  return builder.clone().baseUrl(baseUrl).requestFactory(factory).requestInterceptor(new grupo22.eventocatalogo_service.observability.CorrelationInterceptor()).requestInterceptor(interceptor(target)).requestInterceptor(transport.propagate()).build();
 }
}
