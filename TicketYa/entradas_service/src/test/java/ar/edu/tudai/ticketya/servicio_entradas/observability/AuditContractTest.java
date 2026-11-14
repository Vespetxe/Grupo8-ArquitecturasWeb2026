package ar.edu.tudai.ticketya.servicio_entradas.observability;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditContractTest {
    @AfterEach void clean(){SecurityContextHolder.clearContext();MDC.clear();}
    @Test void jsonContractIncludesControlledErrorCodes()throws Exception{
        var properties=new java.util.Properties();
        try(var input=Audit.class.getResourceAsStream("/logging.properties")){assertNotNull(input);properties.load(input);}
        var included=java.util.Arrays.asList(properties.getProperty("logging.structured.json.include").split(","));
        assertTrue(included.contains("errorCode"));assertTrue(included.contains("actorVerified"));assertTrue(included.contains("eventId"));
    }
    @Test void eventsAreUniqueButCommercialIdentityStaysStable(){
        try(var logs=new AuditCapture()){
            var event=Audit.capture(Audit.Action.PAYMENT_REPLAY,Audit.Resource.PAYMENT,2L,Audit.operation(Audit.Resource.PURCHASE,1L),Audit.Outcome.SUCCESS,null);
            Audit.emit(event);Audit.emit(event);
            var rows=logs.rows();assertEquals(2,rows.size());
            assertNotEquals(rows.get(0).get("eventId"),rows.get(1).get("eventId"));
            for(var row:rows){assertDoesNotThrow(()->UUID.fromString((String)row.get("eventId")));assertEquals("PURCHASE:1",row.get("operationId"));assertNotNull(row.get("timestamp"));}
        }
    }
    @Test void actorUsesAuthenticatedJwtAndNeverAnArbitraryPrincipal(){
        var jwt=Jwt.withTokenValue("sentinel-42-token").header("alg","RS256").subject("42").claim("token_use","user").build();
        var authentication=new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        assertEquals(new Audit.Actor("CLIENTE","42",true),Audit.actor(authentication));
        var arbitrary=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("999","sentinel-42",List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
        assertFalse(Audit.actor(arbitrary).verified());assertNull(Audit.actor(arbitrary).id());
        assertFalse(Audit.actor(null).verified());
    }
    @Test void snapshotsDoNotIncludeSecretsOrArbitraryOperationKeys(){
        MDC.put("password","sentinel-42-password");MDC.put("traceId","sentinel-42-token");
        try(var logs=new AuditCapture()){
            Audit.emit(Audit.capture(Audit.Action.ACCESS_ATTEMPT,Audit.Resource.HTTP,"sentinel-42-body","sentinel-42-idempotency",Audit.Outcome.REJECTED,null));
            var row=logs.rows().get(0);assertFalse(row.toString().contains("sentinel-42"));
            assertFalse(row.containsKey("operationId"));assertFalse(row.containsKey("resourceId"));assertFalse(row.containsKey("trace.id"));
        }
    }
    @Test void loggingFailureDoesNotChangeBusinessResult(){
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(Audit.class);
        var broken=new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>(){
            @Override protected void append(ch.qos.logback.classic.spi.ILoggingEvent event){throw new IllegalStateException("sentinel-42-sink");}
        };
        boolean additive=logger.isAdditive();broken.setContext(logger.getLoggerContext());broken.start();logger.setAdditive(false);logger.addAppender(broken);
        try{assertDoesNotThrow(()->Audit.emit(Audit.capture(Audit.Action.PAYMENT_PROCESS,Audit.Resource.PAYMENT,1L,"PURCHASE:1",Audit.Outcome.SUCCESS,null)));}
        finally{logger.detachAppender(broken);broken.stop();logger.setAdditive(additive);}
    }
}
