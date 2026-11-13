package ar.edu.ticketya.identidad.security;

import ar.edu.ticketya.identidad.repository.TechnicalClientRepository;
import ar.edu.ticketya.identidad.config.JwtProperties;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import java.util.Arrays;
import java.util.HashSet;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClientRegistry implements RegisteredClientRepository {
    private final TechnicalClientRepository clients;
    private final JwtProperties properties;
    public ClientRegistry(TechnicalClientRepository clients,JwtProperties properties){this.clients=clients;this.properties=properties;}
    @Override public void save(RegisteredClient client){throw new UnsupportedOperationException("Use controlled provisioning");}
    @Override public RegisteredClient findById(String id){return findByClientId(id);}
    @Override @Transactional(readOnly=true) public RegisteredClient findByClientId(String id){
        if(id==null)return null;
        return clients.findById(id).filter(c->c.isEnabled())
            .filter(c->TechnicalClientPolicy.allowsStored(c.getClientId(),c.getAudience(),new HashSet<>(Arrays.asList(c.getScopes().split(" ")))))
            .map(c->RegisteredClient.withId(c.getClientId())
            .clientId(c.getClientId()).clientSecret(c.getSecretHash())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scopes(scopes->scopes.addAll(Arrays.asList(c.getScopes().split(" "))))
            .clientSettings(org.springframework.security.oauth2.server.authorization.settings.ClientSettings.builder()
                .setting("ticketya.audience",c.getAudience()).build())
            .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(properties.serviceTtl())
                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256).build()).build()).orElse(null);
    }
}
