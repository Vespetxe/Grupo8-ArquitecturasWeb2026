package grupo22.eventocatalogo_service.reports;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.*;
import grupo22.eventocatalogo_service.SecurityTestKeys;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
class OrganizerReportTest {
 static HttpServer server;static volatile long event,sector;static volatile String failure="",mode="sale";static AtomicInteger calls=new AtomicInteger();
 static {try{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{String path=x.getRequestURI().getPath(),body;int status=200;if(path.equals("/oauth2/token"))body="{\"access_token\":\"report-test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}";else{calls.incrementAndGet();if(failure.equals("down"))status=503;String rows=path.endsWith("/sales")?(mode.equals("empty")?"[]":"[{\"sectorId\":"+sector+",\"soldTickets\":2,\"returnedTickets\":1,\"grossCollected\":30000,\"refundedAmount\":15000,\"netCollected\":15000}]"):path.endsWith("/tickets")?(mode.equals("empty")?"[]":"[{\"sectorId\":"+sector+",\"issuedTickets\":2,\"activeTickets\":1,\"annulledTickets\":1,\"admissions\":0}]"):"[{\"sectorId\":"+sector+",\"capacity\":2,\"available\":1,\"state\":\"HABILITADO\"}]";if(failure.equals("missingCounts")&&path.endsWith("/inventory"))rows="[{\"sectorId\":"+sector+",\"state\":\"HABILITADO\"}]";body=failure.equals("null")?"null":"{\"eventId\":"+event+",\"generatedAt\":\"2030-01-01T00:00:00Z\",\"financialComplete\":true,\"sectors\":"+rows+"}";}byte[] data=body.getBytes();x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,data.length);x.getResponseBody().write(data);x.close();});server.start();}catch(Exception e){throw new IllegalStateException(e);}}
 @DynamicPropertySource static void endpoints(DynamicPropertyRegistry properties){String base="http://127.0.0.1:"+server.getAddress().getPort();for(String service:new String[]{"compras","inventario","entradas"})properties.add("ticketya."+service+".base-url",()->base);properties.add("TY_SECURITY_TOKEN_URL",()->base+"/oauth2/token");}
 @AfterAll static void close(){server.stop(0);}
 @Autowired EventRepository events;@Autowired VenueRepository venues;@Autowired SectorRepository sectors;@Autowired WebApplicationContext context;MockMvc mvc;
 @BeforeEach void setup(){failure="";mode="sale";calls.set(0);var venue=venues.save(new Venue("Report","Address",BigDecimal.ONE,BigDecimal.ONE));var s=sectors.save(new Sector(venue,"Sector",SectorType.NO_NUMERADO,2));sector=s.getSectorId();var e=new Event("Report",null,"Recital",77L,venue.getVenueId(),OffsetDateTime.now().plusDays(8));e.setIdentityOwnerId(77L);e.setStatus(EventStatus.PUBLICADO);event=events.save(e).getEventId();mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();}
 String token(long id,String role)throws Exception{return "Bearer "+SecurityTestKeys.token("eventos","user",String.valueOf(id),role,null,300);}
 @Test void composesSectorRevenueReturnsAndCommercialOccupancy()throws Exception{mvc.perform(get("/api/v1/events/"+event+"/report").header("Authorization",token(77,"ORGANIZADOR"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.soldTickets").value(2)).andExpect(jsonPath("$.totals.returnedTickets").value(1)).andExpect(jsonPath("$.totals.netCollected").value(15000)).andExpect(jsonPath("$.totals.occupancyPercent").value(50)).andExpect(jsonPath("$.sectors[0].metrics.capacity").value(2)).andExpect(jsonPath("$.sourcesAligned").value(true));assertEquals(3,calls.get());}
 @Test void noSalesReturnsVerifiedZeros()throws Exception{mode="empty";mvc.perform(get("/api/v1/events/"+event+"/report").header("Authorization",token(77,"ORGANIZADOR"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.soldTickets").value(0)).andExpect(jsonPath("$.totals.netCollected").value(0));}
 @Test void ownershipAndRolesAreCheckedBeforeAnyRemoteRead()throws Exception{String url="/api/v1/events/"+event+"/report";mvc.perform(get(url)).andExpect(status().isUnauthorized());for(String role:new String[]{"CLIENTE","ADMINISTRADOR","PERSONAL_ACCESO"})mvc.perform(get(url).header("Authorization",token(77,role))).andExpect(status().isForbidden());mvc.perform(get(url).header("Authorization",token(78,"ORGANIZADOR"))).andExpect(status().isNotFound());assertEquals(0,calls.get());}
 @Test void unavailableOrEmptyRemoteResponseCannotBecomeZeroMetrics()throws Exception{for(String kind:new String[]{"down","null","missingCounts"}){failure=kind;mvc.perform(get("/api/v1/events/"+event+"/report").header("Authorization",token(77,"ORGANIZADOR"))).andExpect(status().isServiceUnavailable());}}
 @Test void eventFilterRejectsInvalidIdentifierBeforeRemoteReads()throws Exception{mvc.perform(get("/api/v1/events/0/report").header("Authorization",token(77,"ORGANIZADOR"))).andExpect(status().isBadRequest());assertEquals(0,calls.get());}
}
