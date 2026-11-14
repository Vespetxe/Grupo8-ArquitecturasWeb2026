package ar.edu.unicen.grupo8.comprasservice.security;
import java.nio.file.*;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
public final class JwtTrust {
 private JwtTrust(){}
 public static RSAPublicKey key(Environment env) throws Exception {
  try(var input=Files.newInputStream(Path.of(env.getRequiredProperty("TY_SECURITY_PUBLIC_KEY")))){
   var key=RsaKeyConverters.x509().convert(input);
   if(key==null||key.getModulus().bitLength()<3072)throw new IllegalArgumentException();return key;
  }catch(Exception ex){throw new IllegalStateException("Readable RSA public key of at least 3072 bits required");}
 }
 public static OAuth2TokenValidator<Jwt> validator(Environment env,String service){
  String issuer=env.getRequiredProperty("TY_SECURITY_ISSUER"),kid=env.getRequiredProperty("TY_SECURITY_KEY_ID");
  OAuth2TokenValidator<Jwt> claims=jwt->{boolean valid=false;try{
   String use=jwt.getClaimAsString("token_use");Object roles=jwt.getClaims().get("roles");
   valid=kid.equals(jwt.getHeaders().get("kid"))&&jwt.getAudience().contains("ticketya-"+service)
    &&jwt.getIssuedAt()!=null&&jwt.getNotBefore()!=null&&jwt.getExpiresAt()!=null&&jwt.getId()!=null&&!jwt.getId().isBlank()
    &&!jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))&&!jwt.getNotBefore().isBefore(jwt.getIssuedAt())&&jwt.getExpiresAt().isAfter(jwt.getNotBefore());
   if("user".equals(use))valid=valid&&jwt.getSubject().matches("[1-9][0-9]{0,18}")&&Long.parseLong(jwt.getSubject())>0
    &&roles instanceof List<?> list&&!list.isEmpty()&&list.stream().allMatch(r->r instanceof String&&Set.of("CLIENTE","ORGANIZADOR","ADMINISTRADOR","PERSONAL_ACCESO").contains(r));
   else if("service".equals(use))valid=valid&&!"gateway".equals(service)&&roles==null
    &&jwt.getAudience().equals(List.of("ticketya-"+service))&&jwt.getSubject().matches("service:(eventos|compras|entradas)-to-"+service);
   else valid=false;
  }catch(RuntimeException ex){valid=false;}
  return valid?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));};
  return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(30)),new JwtIssuerValidator(issuer),claims);
 }
}
