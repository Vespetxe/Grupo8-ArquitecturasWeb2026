package ar.edu.tudai.ticketya.servicio_entradas.resilience;
import ar.edu.tudai.ticketya.servicio_entradas.integration.RestComprasOrderClient;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class PilotReadHttpTest {
 HttpServer server;String url;RestComprasOrderClient client;final AtomicInteger calls=new AtomicInteger();final AtomicInteger status=new AtomicInteger(503);final AtomicReference<String> body=new AtomicReference<>("sentinel-secret");ExecutorService workers;ar.edu.tudai.ticketya.servicio_entradas.timing.TimedTransport transport;
 @BeforeEach void setup()throws Exception{
  workers=Executors.newCachedThreadPool();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);server.createContext("/",x->{calls.incrementAndGet();byte[] b=body.get().getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status.get(),b.length);x.getResponseBody().write(b);x.close();});server.start();url="http://127.0.0.1:"+server.getAddress().getPort();
  var env=new MockEnvironment().withProperty("ticketya.resilience.read.window","2").withProperty("ticketya.resilience.read.minimum","2");transport=new ar.edu.tudai.ticketya.servicio_entradas.timing.TimedTransport(env);var rest=RestClient.builder().baseUrl(url).requestFactory(transport.factory(false));client=new RestComprasOrderClient(rest,url,env);
 }
 @AfterEach void stop()throws Exception{server.stop(0);workers.shutdownNow();transport.close();}
 @Test void openCircuitDoesNotDispatchOrInventAReadResult(){for(int i=0;i<2;i++)assertThrows(ar.edu.tudai.ticketya.servicio_entradas.integration.OrdersUnavailableException.class,()->client.getOrderForOwnership("1"));assertThrows(ar.edu.tudai.ticketya.servicio_entradas.integration.OrdersUnavailableException.class,()->client.getOrderForOwnership("1"));assertEquals(2,calls.get());}
 @Test void forbiddenRemainsForbiddenWithoutOpeningCircuit(){status.set(403);for(int i=0;i<12;i++)assertThrows(ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure.class,()->client.getOrderForOwnership("1"));assertEquals(12,calls.get());status.set(200);body.set(" {\"orderId\":\"1\",\"clientId\":\"31\",\"status\":\"CONFIRMADA\",\"items\":[{\"eventId\":\"1\",\"sectorId\":\"1\",\"quantity\":1}]}");assertEquals("31",client.getOrderForOwnership("1").clientId());assertEquals(13,calls.get());}
 @Test void businessConflictRemainsAnErrorButDoesNotOpenCircuit(){status.set(409);for(int i=0;i<12;i++)assertThrows(ar.edu.tudai.ticketya.servicio_entradas.integration.OrdersUnavailableException.class,()->client.getOrderForOwnership("1"));assertEquals(12,calls.get());status.set(200);body.set(" {\"orderId\":\"1\",\"clientId\":\"31\",\"status\":\"CONFIRMADA\",\"items\":[{\"eventId\":\"1\",\"sectorId\":\"1\",\"quantity\":1}]}");assertEquals("31",client.getOrderForOwnership("1").clientId());}
 @Test void invalidSnapshotIsTechnicalAndNeverBecomesStockPriceOrOwnership(){status.set(200);body.set("{}");for(int i=0;i<2;i++)assertThrows(ar.edu.tudai.ticketya.servicio_entradas.integration.OrdersUnavailableException.class,()->client.getOrderForOwnership("1"));assertThrows(ar.edu.tudai.ticketya.servicio_entradas.integration.OrdersUnavailableException.class,()->client.getOrderForOwnership("1"));assertEquals(2,calls.get());}
}
