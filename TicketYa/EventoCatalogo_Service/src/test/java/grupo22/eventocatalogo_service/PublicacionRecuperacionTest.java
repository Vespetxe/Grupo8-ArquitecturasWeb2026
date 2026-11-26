package grupo22.eventocatalogo_service;

import com.sun.net.httpserver.HttpServer;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.*;
import grupo22.eventocatalogo_service.service.*;
import grupo22.eventocatalogo_service.DTO.response.EventResponseDTO;
import grupo22.eventocatalogo_service.exception.BusinessRuleException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicacionRecuperacionTest {
    @Test void rechazoDeAutenticacionNoEsRechazoComercialNiPublicaEvento()throws Exception{
        for(int status:new int[]{401,403}){
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",x->{x.getRequestBody().readAllBytes();x.sendResponseHeaders(status,-1);x.close();});server.start();
            try{
                var f=workflow("http://127.0.0.1:"+server.getAddress().getPort());
                assertEquals(503,assertThrows(ResponseStatusException.class,()->f.workflow.publish(1L)).getStatusCode().value());
                verify(f.service,never()).publishEvent(any());assertEquals(EventStatus.BORRADOR,f.event.getStatus());
            }finally{server.stop(0);}
        }
    }
    private grupo22.eventocatalogo_service.security.ServiceTokens testTokens(String url){
      var tokens=org.mockito.Mockito.mock(grupo22.eventocatalogo_service.security.ServiceTokens.class);
      grupo22.eventocatalogo_service.feign.FeignFixtures.stub(tokens,new org.springframework.core.env.StandardEnvironment());return tokens;
    }
    record Fixture(EventWorkflow workflow,EventService service,EventResponseDTO event) {}
    private Fixture workflow(String url) {
        var service=mock(EventService.class);var repository=mock(EventRepository.class);var sectors=mock(SectorRepository.class);
        var response=new EventResponseDTO();response.setStatus(EventStatus.BORRADOR);
        when(service.getEventById(1L)).thenAnswer(i->response);
        when(service.publishEvent(1L)).thenAnswer(i->{response.setStatus(EventStatus.PUBLICADO);return response;});
        var e=new Event("Evento","Detalle","Recital",1L,2L,OffsetDateTime.now().plusDays(5));
        e.addSectorPrice(new EventSectorPrice(e,10L,BigDecimal.TEN));
        when(repository.findById(1L)).thenReturn(Optional.of(e));
        var sector=new Sector();sector.setSectorId(10L);sector.setType(SectorType.NO_NUMERADO);sector.setCapacity(2);
        when(sectors.findByVenueVenueId(2L)).thenReturn(List.of(sector));
        return new Fixture(new EventWorkflow(service,repository,sectors,mock(PlatformTransactionManager.class),url,url,url,testTokens(url)),service,response);
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={404,409,422,429,500,502,503,504})
    void unconfirmedRemoteRejectionKeepsDraftRecoverable(int status)throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",x->{x.getRequestBody().readAllBytes();x.sendResponseHeaders(status,-1);x.close();});server.start();
        try{var f=workflow("http://127.0.0.1:"+server.getAddress().getPort());
            assertEquals(503,assertThrows(ResponseStatusException.class,()->f.workflow.publish(1L)).getStatusCode().value());
            assertEquals(EventStatus.BORRADOR,f.event.getStatus());verify(f.service,never()).publishEvent(1L);
        }finally{server.stop(0);}
    }
    @Test void fallaDeHabilitacionSeRetomaSobrePublicadoSinReinicializar() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var prepared=new AtomicInteger();var enabled=new AtomicInteger();
        server.createContext("/",x->{x.getRequestBody().readAllBytes();boolean init=x.getRequestURI().getPath().endsWith("/inventario");
            int code;if(init){prepared.incrementAndGet();code=201;}else code=enabled.incrementAndGet()==1?503:200;
            if(init){byte[] b="{\"exitoso\":true,\"mensaje\":\"Inventario inicializado exitosamente\"}".getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(code,b.length);x.getResponseBody().write(b);}
            else x.sendResponseHeaders(code,-1);x.close();});server.start();
        try {
            var f=workflow("http://127.0.0.1:"+server.getAddress().getPort());
            assertEquals(503,assertThrows(ResponseStatusException.class,()->f.workflow.publish(1L)).getStatusCode().value());
            assertEquals(EventStatus.PUBLICADO,f.event.getStatus());
            f.workflow.publish(1L);f.workflow.publish(1L);
            verify(f.service,times(1)).publishEvent(1L);assertEquals(1,prepared.get());assertEquals(3,enabled.get());
        }finally{server.stop(0);}
    }
    @Test void inicializacionIncompatibleEsRechazoPermanenteYConservaBorrador() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",x->{x.getRequestBody().readAllBytes();byte[] body="{\"exitoso\":false,\"mensaje\":\"El inventario para este evento y sector ya existe\"}".getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(400,body.length);x.getResponseBody().write(body);x.close();});server.start();
        try {
            var f=workflow("http://127.0.0.1:"+server.getAddress().getPort());
            assertThrows(BusinessRuleException.class,()->f.workflow.publish(1L));
            verify(f.service,never()).publishEvent(1L);assertEquals(EventStatus.BORRADOR,f.event.getStatus());
        }finally{server.stop(0);}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"","{}","{\"exitoso\":false}","null","broken"})
    void invalidAcknowledgementNeverPublishes(String payload)throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",x->{x.getRequestBody().readAllBytes();byte[] b=payload.getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,b.length==0?-1:b.length);if(b.length>0)x.getResponseBody().write(b);x.close();});server.start();
        try{var f=workflow("http://127.0.0.1:"+server.getAddress().getPort());assertEquals(503,assertThrows(ResponseStatusException.class,()->f.workflow.publish(1L)).getStatusCode().value());verify(f.service,never()).publishEvent(1L);}
        finally{server.stop(0);}
    }
}
