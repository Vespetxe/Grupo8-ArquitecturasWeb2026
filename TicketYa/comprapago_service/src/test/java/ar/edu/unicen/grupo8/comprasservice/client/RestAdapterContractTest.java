package ar.edu.unicen.grupo8.comprasservice.client;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import ar.edu.unicen.grupo8.comprasservice.service.EntradaIdentidadService;
import ar.edu.unicen.grupo8.comprasservice.security.ServiceTokens;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RestAdapterContractTest {
 HttpServer server;String base;ServiceTokens tokens;EntradaIdentidadService identities;
 @BeforeEach void setup()throws Exception{
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();base="http://127.0.0.1:"+server.getAddress().getPort();
  tokens=mock(ServiceTokens.class);ar.edu.unicen.grupo8.comprasservice.feign.FeignFixtures.stub(tokens,new org.springframework.core.env.StandardEnvironment());identities=mock(EntradaIdentidadService.class);
 }
 @AfterEach void close(){server.stop(0);}
 void respond(String body){server.createContext("/",x->{x.getRequestBody().readAllBytes();byte[] b=body.getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,b.length==0?-1:b.length);if(b.length>0)x.getResponseBody().write(b);x.close();});}
 @Test void missingReservationStateCannotTriggerCompensation(){
  respond("{\"idReserva\":1,\"idCliente\":2,\"idEvento\":3,\"idSector\":4,\"cantidad\":1,\"fechaExpiracion\":\"2026-12-01T10:00:00\"}");
  assertEquals(RemoteFailure.Category.INVALID_RESPONSE,assertThrows(RemoteFailure.class,()->new RestReservaClient(base,identities,tokens).consultarReserva(1L)).category());
 }
 @Test void invalidTicketArrayCannotCreateLocalIdentityMappings(){
  respond("[{\"ticketId\":\"4447e5be-d8f1-4d99-804a-38275093ae60\",\"orderId\":\"1\",\"eventId\":\"2\",\"clientId\":\"3\",\"sectorId\":\"4\",\"status\":\"EMITIDA\"},{}]");
  assertThrows(RemoteFailure.class,()->new RestEntradaClient(base,identities,tokens).consultarEntradas(1L));verifyNoInteractions(identities);
 }
 @Test void emptyAndMalformedResponseAreNotAnEmptyTicketList(){
  respond("");assertThrows(RemoteFailure.class,()->new RestEntradaClient(base,identities,tokens).consultarEntradas(1L));verifyNoInteractions(identities);
 }
 @Test void malformedJsonIsControlled(){
  respond("sentinel-secret");var e=assertThrows(RemoteFailure.class,()->new RestEntradaClient(base,identities,tokens).consultarEntradas(1L));assertFalse(e.toString().contains("sentinel-secret"));
 }
 @Test void validEmptyTicketListConfirmsAbsence(){respond("[]");assertTrue(new RestEntradaClient(base,identities,tokens).consultarEntradas(1L).isEmpty());}
 @Test void invalidEventIsNotACommercialRejection(){respond("{\"eventId\":1}");assertThrows(RemoteFailure.class,()->new RestEventoClient(base,tokens).eventoValido(1L));}
 @Test void lostWriteResponseRequiresReadReconciliationWithoutAutomaticPostReplay(){
  var effects=new AtomicInteger();server.createContext("/write",x->{x.getRequestBody().readAllBytes();effects.incrementAndGet();x.close();});
  server.createContext("/state",x->{byte[] b=String.valueOf(effects.get()).getBytes();x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});
  var client=RestClient.builder().baseUrl(base).requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newHttpClient())).build();
  assertThrows(RemoteFailure.class,()->RestSupport.mutate(RestSupport.Operation.RESERVATION_CONFIRM,()->client.post().uri("/write").body("same-operation").retrieve().toBodilessEntity()));
  assertEquals("1",RestSupport.read(RestSupport.Operation.RESERVATION_READ,()->client.get().uri("/state").retrieve().body(String.class)));assertEquals(1,effects.get());
 }
}
