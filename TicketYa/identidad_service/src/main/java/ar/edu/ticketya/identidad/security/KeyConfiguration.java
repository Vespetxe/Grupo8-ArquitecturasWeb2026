package ar.edu.ticketya.identidad.security;

import ar.edu.ticketya.identidad.config.JwtProperties;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.*;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import java.nio.file.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.security.Signature;
import java.util.List;
import ar.edu.ticketya.identidad.entity.Role;

@Configuration
public class KeyConfiguration {
    @Bean RSAKey signingKey(JwtProperties p) throws Exception {
        // Files only, no classpath fallback and no ephemeral key generation in production.
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try {
            if(!Path.of(p.privateKey()).isAbsolute() || !Path.of(p.publicKey()).isAbsolute())throw new IllegalArgumentException();
            try(var input=Files.newInputStream(Path.of(p.privateKey()))) {privateKey=RsaKeyConverters.pkcs8().convert(input);}
            try(var input=Files.newInputStream(Path.of(p.publicKey()))) {publicKey=RsaKeyConverters.x509().convert(input);}
        } catch(Exception ex) {
            // Never include PEM contents, provider diagnostics or supplied paths in errors.
            throw new IllegalStateException("Readable absolute RSA key files in PKCS8/SPKI format required");
        }
        if(privateKey==null || publicKey==null || publicKey.getModulus().bitLength()<3072
            || !publicKey.getModulus().equals(privateKey.getModulus()))
            throw new IllegalStateException("Matching RSA keys of at least 3072 bits required");
        Signature check=Signature.getInstance("SHA256withRSA");
        byte[] challenge="TicketYa key-pair validation".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        check.initSign(privateKey);check.update(challenge);byte[] signature=check.sign();
        check.initVerify(publicKey);check.update(challenge);
        if(!check.verify(signature))throw new IllegalStateException("Matching RSA key pair required");
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(p.keyId()).algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).build();
    }
    @Bean JWKSource<SecurityContext> jwkSource(RSAKey key){return new ImmutableJWKSet<>(new JWKSet(key));}
    @Bean JwtEncoder jwtEncoder(JWKSource<SecurityContext> source){return new NimbusJwtEncoder(source);}
    @Bean JwtDecoder jwtDecoder(RSAKey key,JwtProperties p) throws Exception {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> human = jwt -> {
            boolean valid=false;
            try {
            Object rawRoles=jwt.getClaims().get("roles");
            valid="user".equals(jwt.getClaims().get("token_use"))
                && p.keyId().equals(jwt.getHeaders().get("kid"))
                && jwt.getSubject()!=null && jwt.getSubject().matches("[1-9][0-9]{0,18}") && Long.parseLong(jwt.getSubject())>0
                && jwt.getAudience().contains("ticketya-identidad")
                && jwt.getAudience().stream().allMatch(p.audiences()::contains)
                && jwt.getIssuedAt()!=null && jwt.getNotBefore()!=null && jwt.getExpiresAt()!=null
                && jwt.getId()!=null && !jwt.getId().isBlank()
                && rawRoles instanceof List<?> roles && !roles.isEmpty()
                && roles.stream().allMatch(role->role instanceof String name && java.util.Arrays.stream(Role.values()).anyMatch(r->r.name().equals(name)))
                && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))
                && !jwt.getNotBefore().isBefore(jwt.getIssuedAt()) && jwt.getExpiresAt().isAfter(jwt.getNotBefore());
            } catch(RuntimeException ex){valid=false;}
            return valid?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ofSeconds(30)),new JwtIssuerValidator(p.issuer()),human));
        return decoder;
    }
    @Bean Clock clock(){return Clock.systemUTC();}
}
