package ar.edu.ticketya.identidad;

import ar.edu.ticketya.identidad.entity.*;
import ar.edu.ticketya.identidad.repository.*;
import ar.edu.ticketya.identidad.service.ProvisioningService;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:identity;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
class IdentityHttpTest {
    static final String ISSUER="http://identity-test.invalid";
    static final String PASSWORD=UUID.randomUUID().toString();
    static final Path KEYS=createKeys();
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired ProvisioningService provisioning;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    static Path createKeys(){
        try {
            var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);
            var pair=generator.generateKeyPair();var dir=Files.createTempDirectory("identity-test-");
            Files.writeString(dir.resolve("private.pem"),pem("PRIVATE KEY",pair.getPrivate().getEncoded()));
            Files.writeString(dir.resolve("public.pem"),pem("PUBLIC KEY",pair.getPublic().getEncoded()));return dir;
        }catch(Exception ex){throw new IllegalStateException("Unable to generate isolated test keys");}
    }
    static String pem(String type,byte[] bytes){return "-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes)+"\n-----END "+type+"-----\n";}
    @DynamicPropertySource static void config(DynamicPropertyRegistry p){
        p.add("identity.jwt.issuer",()->ISSUER);
        p.add("identity.jwt.audiences",()->"ticketya-identidad,ticketya-gateway,ticketya-eventos,ticketya-inventario,ticketya-entradas,ticketya-compras");
        p.add("identity.jwt.key-id",()->"isolated-test-key");
        p.add("identity.jwt.private-key",()->KEYS.resolve("private.pem").toString());
        p.add("identity.jwt.public-key",()->KEYS.resolve("public.pem").toString());
    }
    HttpResponse<String> request(String method,String path,Map<String,?> body,String bearer) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
        if(bearer!=null)b.header("Authorization","Bearer "+bearer);
        if(body!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSONObjectUtils.toJSONString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    Map<String,Object> json(HttpResponse<String> response)throws Exception{return JSONObjectUtils.parse(response.body());}
    String registerAndLogin(String email)throws Exception{
        assertEquals(201,request("POST","/auth/register",Map.of("email",email,"password",PASSWORD),null).statusCode());
        var response=request("POST","/auth/login",Map.of("email",email,"password",PASSWORD),null);
        assertEquals(200,response.statusCode());return (String)json(response).get("access_token");
    }
    @Test @Order(1) void registrationHashUniquenessAndNoSensitiveFields()throws Exception{
        var response=request("POST","/auth/register",Map.of("email","One@Example.test","password",PASSWORD),null);
        assertEquals(201,response.statusCode());
        assertFalse(response.body().contains("password"));assertFalse(response.body().contains(PASSWORD));
        var user=users.findByEmail("one@example.test").orElseThrow();
        assertTrue(user.getId()>0);assertEquals(Set.of(Role.CLIENTE),user.getRoles());
        assertTrue(user.getPasswordHash().startsWith("{argon2id-v1}$argon2id$"));assertTrue(passwords.matches(PASSWORD,user.getPasswordHash()));
        assertEquals(409,request("POST","/auth/register",Map.of("email","ONE@example.test","password",PASSWORD),null).statusCode());
    }
    @Test void rejectsInjectedAuthorityAndInvalidInput()throws Exception{
        for(String field:List.of("id","roles","permissions","status")) {
            var input=new HashMap<String,Object>(Map.of("email","injection@example.test","password",PASSWORD));
            input.put(field,field.equals("roles")?List.of("ADMINISTRADOR"):1);
            assertEquals(400,request("POST","/auth/register",input,null).statusCode());
        }
        assertFalse(users.existsByEmail("injection@example.test"));
        assertEquals(400,request("POST","/auth/register",Map.of("email","invalid","password","short"),null).statusCode());
    }
    @Test void loginSignedClaimsAndMe()throws Exception{
        String token=registerAndLogin("claims@example.test");var jwt=decoder.decode(token);
        assertEquals("RS256",jwt.getHeaders().get("alg"));assertEquals("isolated-test-key",jwt.getHeaders().get("kid"));
        assertEquals(ISSUER,jwt.getIssuer().toString());assertNotNull(jwt.getId());
        assertEquals("user",jwt.getClaimAsString("token_use"));assertEquals(List.of("CLIENTE"),jwt.getClaimAsStringList("roles"));
        assertEquals(900,Duration.between(jwt.getIssuedAt(),jwt.getExpiresAt()).toSeconds());
        assertNotNull(jwt.getNotBefore());
        var me=request("GET","/auth/me",null,token);assertEquals(200,me.statusCode());
        assertEquals("claims@example.test",json(me).get("email"));assertFalse(me.body().contains("password"));
        assertEquals(401,request("GET","/auth/me",null,null).statusCode());
    }
    @Test void loginFailuresHaveSamePublicMessage()throws Exception{
        registerAndLogin("bad-password@example.test");
        try(var audit=new ar.edu.ticketya.identidad.observability.AuditCapture()){
        var wrong=request("POST","/auth/login",Map.of("email","bad-password@example.test","password",UUID.randomUUID().toString()),null);
        var missing=request("POST","/auth/login",Map.of("email","missing@example.test","password",UUID.randomUUID().toString()),null);
        assertEquals(401,wrong.statusCode());assertEquals(401,missing.statusCode());assertEquals(wrong.body(),missing.body());
        var rows=audit.rows().stream().filter(r->"LOGIN".equals(r.get("action"))).toList();
        assertEquals(2,rows.size());
        for(var row:rows){assertEquals("REJECTED",row.get("outcome"));assertEquals("AUTHENTICATION_REJECTED",row.get("errorCode"));assertEquals(false,row.get("actorVerified"));assertFalse(row.containsKey("actorId"));}
        }
    }
    @Test void disabledAccountCannotLoginOrUseMe()throws Exception{
        String token=registerAndLogin("disabled@example.test");
        jdbc.update("UPDATE identity_users SET status='DISABLED' WHERE email=?","disabled@example.test");
        assertEquals(401,request("POST","/auth/login",Map.of("email","disabled@example.test","password",PASSWORD),null).statusCode());
        assertEquals(401,request("GET","/auth/me",null,token).statusCode());
    }
    @Test void jwksPublishesOnlyPublicKeyAndUnknownRoutesAreClosed()throws Exception{
        var jwks=request("GET","/oauth2/jwks",null,null);assertEquals(200,jwks.statusCode());
        assertFalse(jwks.body().contains("\"d\":"));assertFalse(jwks.body().contains("PRIVATE"));
        assertTrue(jwks.body().contains("isolated-test-key"));
        String token=registerAndLogin("deny@example.test");
        assertEquals(403,request("POST","/admin/users",Map.of(),token).statusCode());
        String none=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes())+"."+
            Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\"}".getBytes())+".";
        assertEquals(401,request("GET","/auth/me",null,none).statusCode());
    }
    String signed(String issuer,List<String> audience,Instant expires,String tokenUse)throws Exception{
        Instant now=Instant.now();return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("isolated-test-key").build(),
            JwtClaimsSet.builder().issuer(issuer).subject("1").audience(audience).issuedAt(now.minusSeconds(900)).notBefore(now.minusSeconds(900))
                .expiresAt(expires).id(UUID.randomUUID().toString()).claim("token_use",tokenUse).claim("roles",List.of("CLIENTE")).build())).getTokenValue();
    }
    @Test void rejectsTamperedExpiredIssuerAudienceAndServiceTokens()throws Exception{
        String good=registerAndLogin("tamper@example.test");String[] parts=good.split("\\.");
        byte[] signature=Base64.getUrlDecoder().decode(parts[2]);signature[0]^=1;
        String bad=parts[0]+"."+parts[1]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        for(String token:List.of(bad,signed(ISSUER,List.of("ticketya-identidad"),Instant.now().minusSeconds(60),"user"),
            signed("http://wrong.invalid",List.of("ticketya-identidad"),Instant.now().plusSeconds(300),"user"),
            signed(ISSUER,List.of("wrong"),Instant.now().plusSeconds(300),"user"),
            signed(ISSUER,List.of("ticketya-identidad"),Instant.now().plusSeconds(300),"service")))
            assertEquals(401,request("GET","/auth/me",null,token).statusCode());
    }
    @Test @Order(10) void privilegedProvisioningIsControlledAndPersistent()throws Exception{
        assertThrows(IllegalStateException.class,()->provisioning.privilegedUser("too-early@example.test",PASSWORD,Role.ORGANIZADOR));
        Long id=provisioning.firstAdmin("admin@example.test",PASSWORD);
        assertEquals(Set.of(Role.ADMINISTRADOR),users.findById(id).orElseThrow().getRoles());
        assertThrows(IllegalStateException.class,()->provisioning.firstAdmin("another@example.test",PASSWORD));
        provisioning.privilegedUser("organizer@example.test",PASSWORD,Role.ORGANIZADOR);
        provisioning.privilegedUser("staff@example.test",PASSWORD,Role.PERSONAL_ACCESO);
        assertThrows(IllegalStateException.class,()->provisioning.privilegedUser("extra-admin@example.test",PASSWORD,Role.ADMINISTRADOR));
        var login=request("POST","/auth/login",Map.of("email","admin@example.test","password",PASSWORD),null);
        var token=decoder.decode((String)json(login).get("access_token"));
        assertEquals(300,Duration.between(token.getIssuedAt(),token.getExpiresAt()).toSeconds());
        assertEquals(401,request("POST","/admin/users",Map.of(),null).statusCode());
    }
    @Test @Order(11) void clientCredentialsAreScopedAndCannotImpersonateHumans()throws Exception{
        String secret=UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class,()->provisioning.technicalClient("inventario","ticketya-compras",Set.of("orders.read"),secret));
        provisioning.technicalClient("entradas","ticketya-compras",Set.of("orders.read"),secret);
        var response=clientToken(secret,"orders.read");assertEquals(200,response.statusCode());
        String raw=(String)json(response).get("access_token");
        // Parse claims independently; signature is verified with the public key as a recipient would do.
        java.security.interfaces.RSAPublicKey publicKey;
        try(var input=Files.newInputStream(KEYS.resolve("public.pem"))){publicKey=org.springframework.security.converter.RsaKeyConverters.x509().convert(input);}
        var jwt=com.nimbusds.jwt.SignedJWT.parse(raw);assertTrue(jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(publicKey)));
        var c=jwt.getJWTClaimsSet();assertEquals("service",c.getStringClaim("token_use"));assertNull(c.getClaim("roles"));
        assertEquals(List.of("ticketya-compras"),c.getAudience());assertEquals("service:entradas-to-compras",c.getSubject());
        assertEquals(120,Duration.between(c.getIssueTime().toInstant(),c.getExpirationTime().toInstant()).toSeconds());
        assertEquals(401,request("GET","/auth/me",null,raw).statusCode());
        assertEquals(400,clientToken(secret,"tickets.issue").statusCode());
        assertEquals(401,clientToken(UUID.randomUUID().toString(),"orders.read").statusCode());
    }
    @Test @Order(12) void storedClientPolicyAndDisableAreAppliedOnEveryLookup()throws Exception{
        String secret=UUID.randomUUID().toString();
        provisioning.technicalClient("eventos","ticketya-inventario",Set.of("inventory.prepare"),secret);
        assertEquals(200,clientToken("eventos-to-inventario",secret,"inventory.prepare").statusCode());
        jdbc.update("UPDATE identity_technical_clients SET scopes='inventory.prepare orders.read' WHERE client_id='eventos-to-inventario'");
        assertEquals(401,clientToken("eventos-to-inventario",secret,"inventory.prepare").statusCode());
        var registry=new ar.edu.ticketya.identidad.security.ClientRegistry(
            clients,properties);
        assertNull(registry.findByClientId("eventos-to-inventario"));
        jdbc.update("UPDATE identity_technical_clients SET scopes='inventory.prepare', audience='ticketya-compras' WHERE client_id='eventos-to-inventario'");
        assertNull(registry.findByClientId("eventos-to-inventario"));
        jdbc.update("UPDATE identity_technical_clients SET audience='ticketya-inventario', enabled=false WHERE client_id='eventos-to-inventario'");
        assertNull(registry.findByClientId("eventos-to-inventario"));
        assertEquals(401,clientToken("eventos-to-inventario",secret,"inventory.prepare").statusCode());
        jdbc.update("UPDATE identity_technical_clients SET enabled=true WHERE client_id='eventos-to-inventario'");
        assertNotNull(registry.findByClientId("eventos-to-inventario"));
    }
    @Autowired TechnicalClientRepository clients;
    @Autowired ar.edu.ticketya.identidad.config.JwtProperties properties;
    @Test void malformedSignedClaimsAreUnauthorizedRatherThanServerErrors()throws Exception{
        Instant now=Instant.now();
        var baseline=JwtClaimsSet.builder().issuer(ISSUER).subject("1").audience(List.of("ticketya-identidad"))
            .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(300)).id(UUID.randomUUID().toString())
            .claim("token_use","user").claim("roles",List.of("CLIENTE")).build();
        var mutations=new ArrayList<Map<String,Object>>();
        for(Object sub:List.of("9999999999999999999","9999999999999999999999999999","0","-1"))mutations.add(Map.of("sub",sub));
        mutations.add(Map.of("roles",List.of(1)));mutations.add(Map.of("roles",List.of("UNKNOWN")));
        mutations.add(Map.of("nbf",now.plusSeconds(120)));
        mutations.add(Map.of("iat",now.plusSeconds(120),"nbf",now.plusSeconds(120)));
        for(Map<String,Object> mutation:mutations){
            var claims=JwtClaimsSet.from(baseline).claims(c->c.putAll(mutation)).build();
            String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("isolated-test-key").build(),claims)).getTokenValue();
            assertEquals(401,request("GET","/auth/me",null,token).statusCode());
        }
        // A valid signature with an unrecognized kid must not be accepted by the pinned decoder.
        String good=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("isolated-test-key").build(),baseline)).getTokenValue();
        var parsed=com.nimbusds.jwt.SignedJWT.parse(good);
        java.security.interfaces.RSAPrivateKey key;
        try(var input=Files.newInputStream(KEYS.resolve("private.pem"))){key=org.springframework.security.converter.RsaKeyConverters.pkcs8().convert(input);}
        var wrongKid=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID("unrecognized").build(),parsed.getJWTClaimsSet());
        wrongKid.sign(new com.nimbusds.jose.crypto.RSASSASigner(key));
        assertEquals(401,request("GET","/auth/me",null,wrongKid.serialize()).statusCode());
    }
    HttpResponse<String> clientToken(String secret,String scope)throws Exception{
        return clientToken("entradas-to-compras",secret,scope);
    }
    HttpResponse<String> clientToken(String clientId,String secret,String scope)throws Exception{
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/oauth2/token"))
            .header("Authorization","Basic "+Base64.getEncoder().encodeToString((clientId+":"+secret).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope="+URLEncoder.encode(scope,java.nio.charset.StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.ofString());
    }
}
