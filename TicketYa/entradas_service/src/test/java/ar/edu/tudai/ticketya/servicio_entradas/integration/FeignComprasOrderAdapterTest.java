package ar.edu.tudai.ticketya.servicio_entradas.integration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ar.edu.tudai.ticketya.servicio_entradas.feign.EntradasComprasFeignClient;
import ar.edu.tudai.ticketya.servicio_entradas.feign.FeignSupport;
import ar.edu.tudai.ticketya.servicio_entradas.timing.TimedTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
class FeignComprasOrderAdapterTest {
    private HttpServer server;
    private FeignComprasOrderAdapter client;
    private TimedTransport transport;
    @BeforeEach void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        var env = new org.springframework.mock.env.MockEnvironment();
        transport = new TimedTransport(env);
        var feign = FeignSupport.target(EntradasComprasFeignClient.class,
                FeignSupport.transport(transport.factory(false)), "http://localhost:" + server.getAddress().getPort());
        client = new FeignComprasOrderAdapter(feign, env);
    }
    @AfterEach void stopServer() throws IOException { server.stop(0); transport.close(); }
    @Test void readsConfirmedOrderAndItsItems() {
        respond(200, """
                {"orderId":"order-1","clientId":"client-1","status":"CONFIRMADA",
                 "items":[{"eventId":"event-1","sectorId":"campo","seatId":null,"quantity":2}]}
                """);
        OrderSnapshot order = client.getOrder("order-1");
        assertEquals("client-1", order.clientId());
        assertEquals("campo", order.items().getFirst().sectorId());
        assertEquals(2, order.items().getFirst().quantity());
    }
    @Test void rejectsAnOrderThatDoesNotExist() {
        respond(404, "{\"status\":404,\"message\":\"No existe una compra con ID: missing\"}");
        assertThrows(OrderNotFoundException.class, () -> client.getOrder("missing"));
    }
    @Test void rejectsAnOrderThatIsNotConfirmed() {
        respond(200, """
                {"orderId":"order-1","clientId":"client-1","status":"PENDIENTE",
                 "items":[{"eventId":"event-1","sectorId":"campo","seatId":null,"quantity":1}]}
                """);
        assertThrows(OrderNotConfirmedException.class, () -> client.getOrder("order-1"));
    }
    @Test void unknown404IsNotProofOfAbsence(){respond(404,"{}");assertThrows(OrdersUnavailableException.class,()->client.getOrder("order-1"));}
    @Test void hiddenResourceNeverGrantsOwnership(){respond(404,"{\"status\":404,\"message\":\"Resource not found\"}");assertThrows(OrderNotFoundException.class,()->client.getOrderForOwnership("order-1"));}
    @Test void invalidStatusIsUnknownInsteadOfDefinitiveOrderRejection(){respond(200,"{\"orderId\":\"order-1\",\"clientId\":\"client-1\",\"items\":[{\"eventId\":\"event-1\",\"sectorId\":\"s\",\"quantity\":1}]}");assertThrows(OrdersUnavailableException.class,()->client.getOrder("order-1"));}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={401,403,409,422,429,500,502,503,504})
    void technicalStatusNeverBecomesOrderNotConfirmed(int status){
        respond(status,"{\"message\":\"sentinel-secret\"}");
        if(status==401||status==403)assertThrows(ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure.class,()->client.getOrder("order-1"));
        else {var e=assertThrows(OrdersUnavailableException.class,()->client.getOrder("order-1"));assertEquals(false,e.toString().contains("sentinel-secret"));}
    }
    private void respond(int status, String body) {
        server.createContext("/internal/orders/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
