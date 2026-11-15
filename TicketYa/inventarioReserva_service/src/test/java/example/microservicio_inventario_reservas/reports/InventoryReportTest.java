package example.microservicio_inventario_reservas.reports;
import example.microservicio_inventario_reservas.entities.InventarioSector;
import example.microservicio_inventario_reservas.enums.EstadoInventario;
import example.microservicio_inventario_reservas.repositories.InventarioSectorRepository;
import example.microservicio_inventario_reservas.SecurityTestKeys;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryReportTest {
 @Autowired InventarioSectorRepository inventory;@Autowired InventoryReportController reports;@Value("${local.server.port}")int port;long event;
 @BeforeEach void fixture(){event=System.nanoTime();var sector=new InventarioSector();sector.setIdEvento(event);sector.setIdSector(51L);sector.setEsNumerado(false);sector.setCapacidadTotal(2);sector.setDisponibles(1);sector.setEstado(EstadoInventario.HABILITADO);inventory.saveAndFlush(sector);}
 @Test void readsOperationalCapacityAndDoesNotInventDraftStock(){var s=reports.report(event).sectors().get(0);assertEquals(2,s.capacity());assertEquals(1,s.available());assertEquals("HABILITADO",s.state());assertTrue(reports.report(event+1).sectors().isEmpty());}
 int get(String token)throws Exception{var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/internal/reports/events/"+event+"/inventory"));if(token!=null)req.header("Authorization","Bearer "+token);return HttpClient.newHttpClient().send(req.GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();}
 @Test void onlyEventsWithReadScopeCanRead()throws Exception{assertEquals(401,get(null));assertEquals(403,get(SecurityTestKeys.token("inventario","user","1","ORGANIZADOR",null,300)));assertEquals(403,get(SecurityTestKeys.token("inventario","service","service:compras-to-inventario",null,"reports.inventory.read",300)));assertEquals(403,get(SecurityTestKeys.token("inventario","service","service:eventos-to-inventario",null,"inventory.prepare",300)));assertEquals(200,get(SecurityTestKeys.token("inventario","service","service:eventos-to-inventario",null,"reports.inventory.read",300)));}
}
