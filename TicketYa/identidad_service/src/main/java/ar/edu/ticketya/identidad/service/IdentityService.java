package ar.edu.ticketya.identidad.service;

import ar.edu.ticketya.identidad.dto.AuthDtos.*;
import ar.edu.ticketya.identidad.entity.*;
import ar.edu.ticketya.identidad.repository.UserRepository;
import ar.edu.ticketya.identidad.config.JwtProperties;
import ar.edu.ticketya.identidad.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service
public class IdentityService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final String dummyHash;
    public IdentityService(UserRepository users,PasswordEncoder passwords,JwtEncoder encoder,JwtProperties properties,Clock clock){
        this.users=users;this.passwords=passwords;this.encoder=encoder;this.properties=properties;this.clock=clock;
        this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    public static String canonical(String email){return email.strip().toLowerCase(Locale.ROOT);}
    @Transactional public UserView register(Credentials input){
        String email=canonical(input.email);
        if(users.existsByEmail(email))throw new ApiException(HttpStatus.CONFLICT,"Identificador no disponible");
        return UserView.from(users.saveAndFlush(new IdentityUser(email,passwords.encode(input.password),Set.of(Role.CLIENTE))));
    }
    @Transactional(readOnly=true) public TokenResponse login(Credentials input){
        IdentityUser user=users.findByEmail(canonical(input.email)).orElse(null);
        boolean correct=passwords.matches(input.password,user==null?dummyHash:user.getPasswordHash());
        if(!correct || user==null || user.getStatus()!=AccountStatus.ACTIVE)
            throw new ApiException(HttpStatus.UNAUTHORIZED,"Credenciales inválidas");
        Instant now=clock.instant();
        Duration ttl=user.getRoles().equals(Set.of(Role.CLIENTE))?properties.userTtl():properties.privilegedTtl();
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(properties.issuer()).subject(user.getId().toString())
            .audience(properties.audiences()).issuedAt(now).notBefore(now).expiresAt(now.plus(ttl))
            .id(UUID.randomUUID().toString()).claim("token_use","user")
            .claim("roles",user.getRoles().stream().map(Enum::name).sorted().toList()).build();
        JwsHeader header=JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();
        return new TokenResponse(encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue(),"Bearer",ttl.toSeconds());
    }
    @Transactional(readOnly=true) public UserView me(String subject){
        IdentityUser user=users.findById(Long.valueOf(subject)).filter(u->u.getStatus()==AccountStatus.ACTIVE)
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Credenciales inválidas"));
        return UserView.from(user);
    }
}
