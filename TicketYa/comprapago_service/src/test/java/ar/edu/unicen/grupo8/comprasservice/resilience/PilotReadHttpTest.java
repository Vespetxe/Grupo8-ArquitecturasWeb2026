package ar.edu.unicen.grupo8.comprasservice.resilience;
import ar.edu.unicen.grupo8.comprasservice.client.RestEventoClient;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class PilotReadHttpTest {
 HttpServer server;String url;RestEventoClient client;final AtomicInteger calls=new AtomicInteger();final AtomicInteger status=new AtomicInteger(503);final AtomicReference<String> body=new AtomicReference<>("sentinel-secret");ExecutorService workers;ar.edu.unicen.grupo8.comprasservice.timing.TimedTransport transport;
 @BeforeEach void setup()throws Exception{
  workers=Executors.newCachedThreadPool();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);server.createContext("/",x->{calls.incrementAndGet();byte[] b=body.get().getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status.get(),b.length);x.getResponseBody().write(b);x.close();});server.start();url="http://127.0.0.1:"+server.getAddress().getPort();
  var env=new MockEnvironment().withProperty("ticketya.resilience.read.window","2").withProperty("ticketya.resilience.read.minimum","2");transport=new ar.edu.unicen.grupo8.comprasservice.timing.TimedTransport(env);var rest=RestClient.builder().baseUrl(url).requestFactory(transport.factory(false));var tokens=org.mockito.Mockito.mock(ar.edu.unicen.grupo8.comprasservice.security.ServiceTokens.class);ar.edu.unicen.grupo8.comprasservice.feign.FeignFixtures.stub(tokens,env);client=new RestEventoClient(url,tokens,env);
 }
 @AfterEach void stop()throws Exception{server.stop(0);workers.shutdownNow();transport.close();}
 @Test void openCircuitDoesNotDispatchOrInventAReadResult(){for(int i=0;i<2;i++)assertThrows(ar.edu.unicen.grupo8.comprasservice.client.RemoteFailure.class,()->client.eventoValido(1L));assertThrows(ar.edu.unicen.grupo8.comprasservice.exception.ComunicacionPasarelaException.class,()->client.eventoValido(1L));assertEquals(2,calls.get());}
 @Test void forbiddenRemainsForbiddenWithoutOpeningCircuit(){status.set(403);for(int i=0;i<12;i++)assertThrows(ar.edu.unicen.grupo8.comprasservice.security.TechnicalAuthFailure.class,()->client.eventoValido(1L));assertEquals(12,calls.get());status.set(200);body.set(" {\"eventId\":1,\"status\":\"PUBLICADO\",\"startDateTime\":\"2035-01-01T10:00:00Z\",\"sectorPrices\":[{\"sectorId\":1,\"price\":15000}]}");assertTrue(client.eventoValido(1L));assertEquals(13,calls.get());}
 @Test void businessConflictRemainsAnErrorButDoesNotOpenCircuit(){status.set(409);for(int i=0;i<12;i++)assertThrows(ar.edu.unicen.grupo8.comprasservice.client.RemoteFailure.class,()->client.eventoValido(1L));assertEquals(12,calls.get());status.set(200);body.set(" {\"eventId\":1,\"status\":\"PUBLICADO\",\"startDateTime\":\"2035-01-01T10:00:00Z\",\"sectorPrices\":[{\"sectorId\":1,\"price\":15000}]}");assertTrue(client.eventoValido(1L));}
 @Test void invalidSnapshotIsTechnicalAndNeverBecomesStockPriceOrOwnership(){status.set(200);body.set("{}");for(int i=0;i<2;i++)assertThrows(ar.edu.unicen.grupo8.comprasservice.client.RemoteFailure.class,()->client.eventoValido(1L));assertThrows(ar.edu.unicen.grupo8.comprasservice.exception.ComunicacionPasarelaException.class,()->client.eventoValido(1L));assertEquals(2,calls.get());}
}
