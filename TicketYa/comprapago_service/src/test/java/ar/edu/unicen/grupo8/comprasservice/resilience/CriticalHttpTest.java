package ar.edu.unicen.grupo8.comprasservice.resilience;
import ar.edu.unicen.grupo8.comprasservice.client.*;
import ar.edu.unicen.grupo8.comprasservice.service.EntradaIdentidadService;
import ar.edu.unicen.grupo8.comprasservice.security.ServiceTokens;
import ar.edu.unicen.grupo8.comprasservice.timing.TimedTransport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import com.sun.net.httpserver.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CriticalHttpTest {
    static final String UUID="4447e5be-d8f1-4d99-804a-38275093ae60";
    HttpServer server;ExecutorService workers;TimedTransport transport;RestReservaClient reservas;RestEntradaClient entradas;
    final AtomicReference<String> mode=new AtomicReference<>("NONE");final AtomicInteger confirmations=new AtomicInteger(),issues=new AtomicInteger(),batches=new AtomicInteger();final AtomicBoolean confirmed=new AtomicBoolean(),issued=new AtomicBoolean();final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);final Queue<String> orders=new ConcurrentLinkedQueue<>();
    @BeforeEach void start()throws Exception{
        workers=Executors.newCachedThreadPool();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);server.createContext("/",this::handle);server.start();String url="http://127.0.0.1:"+server.getAddress().getPort();
        var env=new MockEnvironment().withProperty("ticketya.resilience.read.window","2").withProperty("ticketya.resilience.read.minimum","2").withProperty("ticketya.resilience.read.concurrent","1");transport=new TimedTransport(env);var tokens=mock(ServiceTokens.class);when(tokens.client(anyString(),anyString())).thenReturn(RestClient.builder().baseUrl(url).requestFactory(transport.factory(false)).requestInterceptor(transport.propagate()).build());var identity=mock(EntradaIdentidadService.class);when(identity.registrar(UUID)).thenReturn(501L);reservas=new RestReservaClient(url,identity,tokens,env);entradas=new RestEntradaClient(url,identity,tokens,env);
    }
    @AfterEach void stop()throws Exception{release.countDown();server.stop(0);workers.shutdownNow();transport.close();}
    void response(HttpExchange x,int code,String body)throws java.io.IOException{byte[] bytes=body.getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(code,code==204?-1:bytes.length);if(code!=204)x.getResponseBody().write(bytes);x.close();}
    void handle(HttpExchange x)throws java.io.IOException{
        String path=x.getRequestURI().getPath();
        if(x.getRequestMethod().equals("POST")){
            boolean confirmation=path.equals("/reservas/30/confirmar");String body=new String(x.getRequestBody().readAllBytes());if(confirmation)confirmations.incrementAndGet();else{issues.incrementAndGet();orders.add(body);}
            String kind=confirmation?"CONFIRM":"ISSUE",fault=mode.get();if(fault.equals(kind+"_503")){response(x,503,"{}");return;}
            if(fault.equals(kind+"_HOLD")){entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            if(confirmation)confirmed.set(true);else if(issued.compareAndSet(false,true))batches.incrementAndGet();
            if(fault.equals(kind+"_LOST")){x.close();return;}if(fault.equals(kind+"_TIMEOUT")){try{Thread.sleep(400);}catch(InterruptedException e){Thread.currentThread().interrupt();}}response(x,204,"");return;
        }
        if(path.equals("/reservas/30")){response(x,200,"{\"idReserva\":30,\"idCliente\":10,\"idEvento\":20,\"idSector\":1,\"cantidad\":1,\"fechaExpiracion\":\"2035-01-01T10:00:00\",\"estado\":\""+(confirmed.get()?"CONFIRMADA":"PENDIENTE")+"\",\"asientosReservados\":[]}");return;}
        if(path.equals("/tickets")){response(x,200,issued.get()?"[{\"ticketId\":\""+UUID+"\",\"orderId\":\"1\",\"eventId\":\"20\",\"clientId\":\"10\",\"sectorId\":\"1\",\"status\":\"EMITIDA\"}]":"[]");return;}
        response(x,200,"[]");
    }
    String kind(PilotReadGuard.Family family){return family==PilotReadGuard.Family.RESERVATION_CONFIRM?"CONFIRM":"ISSUE";}
    void command(PilotReadGuard.Family family){if(family==PilotReadGuard.Family.RESERVATION_CONFIRM)reservas.confirmarReserva(30L);else entradas.solicitarEntradas(1L,20L,10L,List.of());}
    int writes(PilotReadGuard.Family family){return family==PilotReadGuard.Family.RESERVATION_CONFIRM?confirmations.get():issues.get();}
    PilotReadGuard gate(PilotReadGuard.Family family){return (PilotReadGuard)ReflectionTestUtils.getField(family==PilotReadGuard.Family.RESERVATION_CONFIRM?reservas:entradas,family==PilotReadGuard.Family.RESERVATION_CONFIRM?"confirmation":"issue");}
    void confirmResult(PilotReadGuard.Family family){if(family==PilotReadGuard.Family.RESERVATION_CONFIRM)assertEquals("CONFIRMADA",reservas.consultarReserva(30L).estado());else{var tickets=entradas.consultarEntradas(1L);assertEquals(1,tickets.size());assertEquals(501L,tickets.getFirst().entradaId());assertEquals(1,batches.get());assertTrue(orders.stream().allMatch(s->s.contains("\"orderId\":\"1\"")));}}
    @ParameterizedTest @EnumSource(value=PilotReadGuard.Family.class,names={"RESERVATION_CONFIRM","TICKETS_ISSUE"})
    void openCommandDoesNotDispatchAndDoesNotBlockReadReconciliation(PilotReadGuard.Family family){mode.set(kind(family)+"_503");for(int i=0;i<2;i++)assertEquals(RemoteFailure.Category.UNAVAILABLE,assertThrows(RemoteFailure.class,()->command(family)).category());var rejected=assertThrows(PilotReadGuard.Unavailable.class,()->command(family));assertFalse(rejected.dispatched());assertEquals("CIRCUIT_OPEN",rejected.category());assertEquals(2,writes(family));assertNotNull(reservas.consultarReserva(30L));assertNotNull(entradas.consultarEntradas(1L));mode.set("NONE");gate(family).circuit().transitionToHalfOpenState();command(family);confirmResult(family);assertEquals(3,writes(family));}
    @ParameterizedTest @EnumSource(value=PilotReadGuard.Family.class,names={"RESERVATION_CONFIRM","TICKETS_ISSUE"})
    void committedWriteWithLostResponseIsUnknownAndIsReconciledWithoutReplay(PilotReadGuard.Family family){mode.set(kind(family)+"_LOST");var unknown=assertThrows(RemoteFailure.class,()->command(family));assertEquals(RemoteFailure.Category.RESULT_UNKNOWN,unknown.category());assertEquals(1,writes(family));confirmResult(family);confirmResult(family);assertEquals(1,writes(family));}

    @ParameterizedTest @EnumSource(value=PilotReadGuard.Family.class,names={"RESERVATION_CONFIRM","TICKETS_ISSUE"})
    void committedWriteTimeoutIsUnknownAndReadsRecoverWithoutAnotherWrite(PilotReadGuard.Family family)throws Exception{
        var env=new MockEnvironment().withProperty("ticketya.http.command-ms","60");
        try(var slow=new TimedTransport(env)){
            var url="http://127.0.0.1:"+server.getAddress().getPort();var tokens=mock(ServiceTokens.class);when(tokens.client(anyString(),anyString())).thenReturn(RestClient.builder().baseUrl(url).requestFactory(slow.factory(false)).requestInterceptor(slow.propagate()).build());var identity=mock(EntradaIdentidadService.class);when(identity.registrar(UUID)).thenReturn(501L);reservas=new RestReservaClient(url,identity,tokens,env);entradas=new RestEntradaClient(url,identity,tokens,env);
            mode.set(kind(family)+"_TIMEOUT");long start=System.nanoTime();assertEquals(RemoteFailure.Category.RESULT_UNKNOWN,assertThrows(RemoteFailure.class,()->command(family)).category());long elapsed=(System.nanoTime()-start)/1_000_000;assertTrue(elapsed>=30&&elapsed<2000);assertEquals(1,writes(family));confirmResult(family);assertEquals(1,writes(family));
        }
    }

    @ParameterizedTest @EnumSource(value=PilotReadGuard.Family.class,names={"RESERVATION_CONFIRM","TICKETS_ISSUE"})
    void saturatedCommandRejectsBeforeDispatchAndLeavesReadsAvailable(PilotReadGuard.Family family)throws Exception{mode.set(kind(family)+"_HOLD");var first=workers.submit(()->command(family));try{assertTrue(entered.await(2,TimeUnit.SECONDS));var full=assertThrows(PilotReadGuard.Unavailable.class,()->command(family));assertFalse(full.dispatched());assertEquals("BULKHEAD_FULL",full.category());assertEquals(1,writes(family));assertNotNull(reservas.consultarReserva(30L));assertNotNull(entradas.consultarEntradas(1L));}finally{release.countDown();}first.get(3,TimeUnit.SECONDS);confirmResult(family);assertEquals(1,writes(family));}
}
