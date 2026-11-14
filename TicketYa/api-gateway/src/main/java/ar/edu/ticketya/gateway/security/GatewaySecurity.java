package ar.edu.ticketya.gateway.security;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.authorization.AuthorizationDecision;
import reactor.core.publisher.Mono;
@Configuration
public class GatewaySecurity {
 @Bean ReactiveJwtDecoder decoder(Environment e)throws Exception{var d=NimbusReactiveJwtDecoder.withPublicKey(JwtTrust.key(e)).signatureAlgorithm(SignatureAlgorithm.RS256).build();d.setJwtValidator(JwtTrust.validator(e,"gateway"));return d;}
 @Bean SecurityWebFilterChain security(ServerHttpSecurity h){
  var c=new JwtAuthenticationConverter();c.setJwtGrantedAuthoritiesConverter(jwt->{
   var values=new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>(new JwtGrantedAuthoritiesConverter().convert(jwt));
   jwt.getClaimAsStringList("roles").forEach(r->values.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+r)));return values;});
  return h.csrf(x->x.disable()).httpBasic(x->x.disable()).formLogin(x->x.disable())
   .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
   .addFilterAfter(new ar.edu.ticketya.gateway.observability.GatewayActorCaptureFilter(),org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
   .authorizeExchange(x->x.anyExchange().access((auth,context)->auth.<org.springframework.security.authorization.AuthorizationResult>map(a->new AuthorizationDecision(RoutePolicy.allow("gateway",context.getExchange().getRequest().getMethod().name(),context.getExchange().getRequest().getPath().value(),a)))
    .defaultIfEmpty(new AuthorizationDecision(RoutePolicy.allow("gateway",context.getExchange().getRequest().getMethod().name(),context.getExchange().getRequest().getPath().value(),null)))))
   .oauth2ResourceServer(x->x.jwt(j->j.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(c)))).build();
 }
 @Bean GlobalFilter removeUntrustedIdentityHeaders(){return (exchange,chain)->chain.filter(exchange.mutate().request(exchange.getRequest().mutate().headers(headers->{
  headers.remove("X-User-Role");headers.remove("X-Admin-Id");headers.remove("X-User-Id");headers.remove("X-Delegated-Authorization");
 }).build()).build());}
}
