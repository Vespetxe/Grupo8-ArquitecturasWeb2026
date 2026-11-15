package ar.edu.tudai.ticketya.servicio_entradas.reports;
import ar.edu.tudai.ticketya.servicio_entradas.domain.*;
import ar.edu.tudai.ticketya.servicio_entradas.repository.*;
import ar.edu.tudai.ticketya.servicio_entradas.SecurityTestKeys;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.time.Instant;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketReportTest {
 @Autowired TicketRepository tickets;@Autowired TicketBatchRepository batches;@Autowired TicketReportController reports;@Value("${local.server.port}")int port;long event;
 @BeforeEach void fixture(){event=System.nanoTime();var batch=batches.saveAndFlush(new TicketBatch(UUID.randomUUID().toString()));var a=new Ticket(UUID.randomUUID().toString(),UUID.randomUUID().toString(),batch,String.valueOf(event),"1","51",null);a.recordAdmission(Instant.now(),1L);a.setStatus(TicketStatus.ANULADA);tickets.saveAndFlush(a);tickets.saveAndFlush(new Ticket(UUID.randomUUID().toString(),UUID.randomUUID().toString(),batch,String.valueOf(event),"1","51",null));}
 @Test void aggregatesStatusesAndRetainsHistoricalAdmission(){var result=reports.report(event);assertEquals(1,result.sectors().size());var sector=result.sectors().get(0);assertEquals(2,sector.issuedTickets());assertEquals(1,sector.activeTickets());assertEquals(1,sector.annulledTickets());assertEquals(1,sector.admissions());assertTrue(reports.report(event+1).sectors().isEmpty());}
 int get(String token)throws Exception{var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/internal/reports/events/"+event+"/tickets"));if(token!=null)req.header("Authorization","Bearer "+token);return HttpClient.newHttpClient().send(req.GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();}
 @Test void onlyEventsWithReadScopeCanRead()throws Exception{assertEquals(401,get(null));assertEquals(403,get(SecurityTestKeys.token("entradas","user","1","ORGANIZADOR",null,300)));assertEquals(403,get(SecurityTestKeys.token("entradas","service","service:compras-to-entradas",null,"reports.tickets.read",300)));assertEquals(403,get(SecurityTestKeys.token("entradas","service","service:eventos-to-entradas",null,"tickets.event-block",300)));assertEquals(200,get(SecurityTestKeys.token("entradas","service","service:eventos-to-entradas",null,"reports.tickets.read",300)));}
}
