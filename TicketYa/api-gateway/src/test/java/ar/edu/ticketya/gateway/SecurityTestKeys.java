package ar.edu.ticketya.gateway;
import java.nio.file.*;import java.security.*;import java.time.*;import java.util.*;
import org.springframework.context.*;import org.springframework.boot.test.util.TestPropertyValues;
public class SecurityTestKeys implements ApplicationContextInitializer<ConfigurableApplicationContext>{
 static final KeyPair PAIR=keys();static final Path PUBLIC=write();
 static KeyPair keys(){try{var g=KeyPairGenerator.getInstance("RSA");g.initialize(3072);return g.generateKeyPair();}catch(Exception e){throw new IllegalStateException(e);}}
 static Path write(){try{var p=Files.createTempFile("ticketya-public-test-",".pem");Files.writeString(p,"-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(PAIR.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");return p;}catch(Exception e){throw new IllegalStateException(e);}}
 public void initialize(ConfigurableApplicationContext c){TestPropertyValues.of("TY_SECURITY_PUBLIC_KEY="+PUBLIC,"TY_SECURITY_ISSUER=http://identity-test.invalid","TY_SECURITY_KEY_ID=test-key","TY_SECURITY_TOKEN_URL="+c.getEnvironment().getProperty("TY_SECURITY_TOKEN_URL","http://127.0.0.1:1/oauth2/token"),
  "TY_SECURITY_EVENTOS_SECRET="+UUID.randomUUID(),"TY_SECURITY_INVENTARIO_SECRET="+UUID.randomUUID(),"TY_SECURITY_ENTRADAS_SECRET="+UUID.randomUUID(),"TY_SECURITY_COMPRAS_SECRET="+UUID.randomUUID()).applyTo(c);}
 public static String token(String audience,String use,String sub,String role,String scope,long lifetime)throws Exception{
  var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("http://identity-test.invalid").audience("ticketya-"+audience).subject(sub).issueTime(Date.from(Instant.now().minusSeconds(1))).notBeforeTime(Date.from(Instant.now().minusSeconds(1))).expirationTime(Date.from(Instant.now().plusSeconds(lifetime))).jwtID(UUID.randomUUID().toString()).claim("token_use",use);
  if(role!=null)claims.claim("roles",List.of(role));if(scope!=null)claims.claim("scope",scope);
  var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID("test-key").build(),claims.build());jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner((java.security.interfaces.RSAPrivateKey)PAIR.getPrivate()));return jwt.serialize();
 }
}
