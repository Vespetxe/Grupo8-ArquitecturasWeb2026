package grupo22.eventocatalogo_service.observability;
import org.junit.jupiter.api.Test;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.*;
import grupo22.eventocatalogo_service.service.*;
import grupo22.eventocatalogo_service.DTO.response.EventResponseDTO;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.OffsetDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CancellationAuditTest {
    @Test void differentAdministratorRetryKeepsPersistedOriginalAuthority()throws Exception{
        cancellation("{\"eventoId\":1,\"devoluciones\":[],\"comprasPendientes\":[]}",true);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"","{}","null","broken","{\"eventoId\":2,\"devoluciones\":[],\"comprasPendientes\":[]}","{\"eventoId\":1,\"devoluciones\":[],\"comprasPendientes\":[1]}"})
    void unknownCancellationResultStaysPendingAndPreservesOriginalAdministrator(String payload)throws Exception{cancellation(payload,false);}
    private void cancellation(String payload,boolean success)throws Exception{
        var database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",request->{request.getRequestBody().readAllBytes();
            if(request.getRequestURI().getPath().startsWith("/admin/")){byte[] body=payload.getBytes();request.getResponseHeaders().set("Content-Type","application/json");request.sendResponseHeaders(200,body.length==0?-1:body.length);if(body.length>0)request.getResponseBody().write(body);}
            else request.sendResponseHeaders(200,-1);request.close();});server.start();
        var event=new Event("test","test","Recital",1L,1L,OffsetDateTime.now().plusDays(8));
        event.authorizeCancellation(41L,"original-authorization");
        var response=new EventResponseDTO();response.setEventId(1L);response.setStatus(EventStatus.CANCELADO);
        var events=mock(EventService.class);when(events.getEventById(1L)).thenReturn(response);
        var repository=mock(EventRepository.class);when(repository.lockForCancellation(1L)).thenReturn(Optional.of(event));
        var tokens=mock(grupo22.eventocatalogo_service.security.ServiceTokens.class);
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        grupo22.eventocatalogo_service.feign.FeignFixtures.stub(tokens,new org.springframework.core.env.StandardEnvironment());
        var workflow=new EventWorkflow(events,repository,mock(SectorRepository.class),new DataSourceTransactionManager(database),base,base,base,tokens);
        var jwt=Jwt.withTokenValue("sentinel-42-token").header("alg","RS256").subject("42").claim("token_use","user").claim("jti","retry-authorization").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))));
        try(var audit=new AuditCapture()){
            if(success)assertEquals(EventStatus.CANCELADO,workflow.cancel(1L,"ADMIN",42L).getStatus());
            else assertEquals(503,assertThrows(org.springframework.web.server.ResponseStatusException.class,()->workflow.cancel(1L,"ADMIN",42L)).getStatusCode().value());
            var record=audit.find("EVENT_CANCELLATION_RETRY");
            assertEquals(success?"SUCCESS":"PENDING",record.get("outcome"));assertEquals("42",record.get("actorId"));
            assertEquals(41L,record.get("initiatorId"));assertEquals("ADMINISTRADOR",record.get("initiatorType"));
            assertEquals(41L,event.getCancellationAuthorizedAdminId());verify(events,never()).cancelEvent(any(),any());
        }finally{SecurityContextHolder.clearContext();server.stop(0);database.shutdown();}
    }
}
