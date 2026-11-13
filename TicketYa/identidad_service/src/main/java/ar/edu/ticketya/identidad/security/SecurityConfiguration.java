package ar.edu.ticketya.identidad.security;

import ar.edu.ticketya.identidad.config.JwtProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.web.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import java.util.*;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean @Order(1) SecurityFilterChain authorizationServer(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(server->{
            http.securityMatcher(server.getEndpointsMatcher());
        }).authorizeHttpRequests(a->a.anyRequest().authenticated())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
    @Bean @Order(2) SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/auth/register","/auth/login").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/auth/me").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(o->o.jwt(j->{}))
            .exceptionHandling(e->e.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        return http.build();
    }
    @Bean AuthorizationServerSettings authorizationServerSettings(JwtProperties p){
        return AuthorizationServerSettings.builder().issuer(p.issuer()).build();
    }
    @Bean OAuth2AuthorizationService authorizationService(){return new InMemoryOAuth2AuthorizationService();}
    @Bean OAuth2TokenCustomizer<JwtEncodingContext> serviceClaims(){return context->{
        if(!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType()))
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE);
        context.getJwsHeader().algorithm(SignatureAlgorithm.RS256);
        context.getClaims().subject("service:"+context.getRegisteredClient().getClientId())
            .audience(List.of((String)context.getRegisteredClient().getClientSettings().getSetting("ticketya.audience")))
            .notBefore(context.getClaims().build().getIssuedAt())
            .id(UUID.randomUUID().toString()).claim("token_use","service");
    };}
}
