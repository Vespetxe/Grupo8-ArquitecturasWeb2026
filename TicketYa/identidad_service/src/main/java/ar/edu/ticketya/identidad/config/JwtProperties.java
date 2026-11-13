package ar.edu.ticketya.identidad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.List;

@Validated @ConfigurationProperties("identity.jwt")
public record JwtProperties(@NotBlank String issuer, @NotEmpty List<@NotBlank String> audiences,
    @NotBlank String privateKey, @NotBlank String publicKey, @NotBlank String keyId,
    @NotNull Duration userTtl, @NotNull Duration privilegedTtl, @NotNull Duration serviceTtl) {
    public JwtProperties {
        for(Duration ttl : new Duration[]{userTtl,privilegedTtl,serviceTtl}) {
            if(ttl!=null && (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(15))>0))
                throw new IllegalArgumentException("JWT TTL must be within (0,15m]");
        }
        if(issuer!=null) {
            try {
                var uri=java.net.URI.create(issuer);
                if((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost()==null
                    || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null)
                    throw new IllegalArgumentException();
            } catch(IllegalArgumentException ex){throw new IllegalArgumentException("Issuer must be an absolute HTTP(S) URI without credentials, query or fragment");}
        }
        if(audiences!=null && !audiences.contains("ticketya-identidad"))
            throw new IllegalArgumentException("Human audiences must include ticketya-identidad");
        if(serviceTtl!=null && serviceTtl.compareTo(Duration.ofMinutes(2))>0)
            throw new IllegalArgumentException("Service TTL must not exceed 2m");
        if(privilegedTtl!=null && privilegedTtl.compareTo(Duration.ofMinutes(5))>0)
            throw new IllegalArgumentException("Privileged TTL must not exceed 5m");
    }
}
