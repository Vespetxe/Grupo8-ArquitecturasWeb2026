package grupo22.eventocatalogo_service.security;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.authorization.AuthorizationDecision;
@Configuration
public class DistributedSecurity {
 @Bean JwtDecoder jwtDecoder(Environment e)throws Exception{var d=NimbusJwtDecoder.withPublicKey(JwtTrust.key(e)).signatureAlgorithm(SignatureAlgorithm.RS256).build();d.setJwtValidator(JwtTrust.validator(e,"eventos"));return d;}
 @Bean JwtAuthenticationConverter converter(){var c=new JwtAuthenticationConverter();c.setJwtGrantedAuthoritiesConverter(jwt->{
  var roles=new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>(new JwtGrantedAuthoritiesConverter().convert(jwt));
  if("user".equals(jwt.getClaimAsString("token_use")))jwt.getClaimAsStringList("roles").forEach(r->roles.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+r)));return roles;});return c;}
 @Bean SecurityFilterChain security(HttpSecurity h,JwtAuthenticationConverter c)throws Exception{
  return h.csrf(x->x.disable()).sessionManagement(x->x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .exceptionHandling(x->x.authenticationEntryPoint(SecurityErrors::unauthorized).accessDeniedHandler(SecurityErrors::forbidden))
   .authorizeHttpRequests(x->x.anyRequest().access((authentication,context)->new AuthorizationDecision(RoutePolicy.allow("eventos",context.getRequest().getMethod(),context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length()),authentication.get()))))
   .addFilterAfter(new TrustedActorHeaders(),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
   .oauth2ResourceServer(x->x.authenticationEntryPoint(SecurityErrors::unauthorized).accessDeniedHandler(SecurityErrors::forbidden).jwt(j->j.jwtAuthenticationConverter(c))).build();
 }
}
