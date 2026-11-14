package grupo22.eventocatalogo_service.security;
import org.springframework.stereotype.Component;import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.*;import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;import org.springframework.http.client.*;import org.springframework.web.client.RestClient;
import java.util.*;import java.time.Duration;
@Component
public class ServiceTokens {
 private final AuthorizedClientServiceOAuth2AuthorizedClientManager manager;private final OAuth2AuthorizedClientService cache;
 private final RestClient.Builder builder;
 private final String principal="service:eventos";
 @org.springframework.beans.factory.annotation.Autowired
 public ServiceTokens(Environment e,org.springframework.beans.factory.ObjectProvider<RestClient.Builder> builders){this(e,java.time.Clock.systemUTC(),builders.getObject());}
 public ServiceTokens(Environment e){this(e,java.time.Clock.systemUTC());}
 public ServiceTokens(Environment e,java.time.Clock clock){this(e,clock,RestClient.builder());}
 public ServiceTokens(Environment e,java.time.Clock clock,RestClient.Builder builder){
  this.builder=builder;
  var registrations=new ArrayList<ClientRegistration>();
  for(String target:List.of("inventario","entradas","compras")){
   String secret=e.getRequiredProperty("TY_SECURITY_"+target.toUpperCase(Locale.ROOT)+"_SECRET");
   if(secret.isBlank()||secret.length()<32)throw new IllegalStateException("Independent technical secret of at least 32 characters required");
   registrations.add(ClientRegistration.withRegistrationId(target)
   .tokenUri(e.getRequiredProperty("TY_SECURITY_TOKEN_URL")).clientId("eventos-to-"+target)
   .clientSecret(secret)
   .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope(RoutePolicy.requestedScopes("eventos",target)).build());
  }
  var repo=new InMemoryClientRegistrationRepository(registrations);cache=new InMemoryOAuth2AuthorizedClientService(repo);
  manager=new AuthorizedClientServiceOAuth2AuthorizedClientManager(repo,cache);
  var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(10));
  var responseClient=new org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient();
  responseClient.setRestClient(builder.clone().requestFactory(factory).requestInterceptor(new grupo22.eventocatalogo_service.observability.CorrelationInterceptor()).messageConverters(c->{c.clear();c.add(new org.springframework.http.converter.FormHttpMessageConverter());c.add(new org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter());})
   .defaultStatusHandler(new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler()).build());
  manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials(c->c.clock(clock).clockSkew(Duration.ofSeconds(30)).accessTokenResponseClient(responseClient)).build());
 }
 public synchronized String token(String target){try{
  var client=manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(target).principal(principal).build());
  if(client==null)throw new TechnicalAuthFailure();return client.getAccessToken().getTokenValue();
 }catch(RuntimeException ex){throw new TechnicalAuthFailure();}}
 public ClientHttpRequestInterceptor interceptor(String target){return (request,body,execution)->{
  request.getHeaders().setBearerAuth(token(target));
  var response=execution.execute(request,body);
  if(response.getStatusCode().value()==401||response.getStatusCode().value()==403){
   cache.removeAuthorizedClient(target,principal);response.close();throw new TechnicalAuthFailure();
  }return response;
 };}
 public RestClient client(String target,String baseUrl){
  var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(10));
  return builder.clone().baseUrl(baseUrl).requestFactory(factory).requestInterceptor(new grupo22.eventocatalogo_service.observability.CorrelationInterceptor()).requestInterceptor(interceptor(target)).build();
 }
}
