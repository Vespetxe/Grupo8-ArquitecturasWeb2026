package ar.edu.tudai.ticketya.servicio_entradas.access;
import ar.edu.tudai.ticketya.servicio_entradas.domain.*;
import ar.edu.tudai.ticketya.servicio_entradas.repository.*;
import ar.edu.tudai.ticketya.servicio_entradas.service.*;
import ar.edu.tudai.ticketya.servicio_entradas.integration.*;
import ar.edu.tudai.ticketya.servicio_entradas.SecurityTestKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class AdmissionIntegrationTest {
 @Autowired AdmissionService access;
 @Autowired TicketBatchWriter writer;
 @Autowired TicketRepository tickets;
 @Autowired EventAccessRepository assignments;
 @Autowired TicketStatusService statuses;
 @Autowired TicketAnnulmentService annulment;
 @Autowired EmissionGateService gates;
 @Autowired PlatformTransactionManager manager;
 @Autowired MockMvc mvc;
 String event,order;Ticket ticket;
 void actor(long id,String role){var jwt=Jwt.withTokenValue("unit-test-only").header("alg","RS256").subject(String.valueOf(id)).claim("token_use","user").claim("roles",List.of(role)).build();SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
 @BeforeEach void fixture(){event=UUID.randomUUID().toString();order="qr-"+event;ticket=writer.create(new OrderSnapshot(order,"client","CONFIRMADA",List.of(new OrderItemSnapshot(event,"sector",null,1)))).get(0);actor(1,"ADMINISTRADOR");access.assign(event,10L,true);actor(10,"PERSONAL_ACCESO");}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void firstAdmissionPersistsAndSecondIsRejected(){
  assertNotEquals(ticket.getTicketId(),ticket.getQrPayload());assertEquals(4,UUID.fromString(ticket.getQrPayload()).version());
  var first=access.validate(event,ticket.getQrPayload());assertEquals(200,first.status());assertTrue(first.result().accepted());assertNotNull(first.result().validatedAt());
  var stored=tickets.findById(ticket.getTicketId()).orElseThrow();assertEquals(10L,stored.getValidatedBy());assertEquals(first.result().validatedAt(),stored.getValidatedAt());
  assertEquals(TicketStatus.EMITIDA,stored.getStatus());assertEquals("ALREADY_USED",access.validate(event,ticket.getQrPayload()).result().code());
  assertEquals(stored.getValidatedAt(),tickets.findById(ticket.getTicketId()).orElseThrow().getValidatedAt());
 }
 @Test void invalidAndUnknownTokensDoNotModifyTicket(){
  for(String invalid:new String[]{"", "not-a-uuid",ticket.getQrPayload().toUpperCase(),"Bearer private"})assertEquals(400,access.validate(event,invalid).status());
  assertEquals(400,access.validate(event,null).status());assertEquals(404,access.validate(event,UUID.randomUUID().toString()).status());assertNull(tickets.findById(ticket.getTicketId()).orElseThrow().getValidatedAt());
 }
 @Test void wrongEventIsRejectedEvenWhenAssigned(){
  String other=UUID.randomUUID().toString();actor(1,"ADMINISTRADOR");access.assign(other,10L,true);actor(10,"PERSONAL_ACCESO");assertEquals("EVENT_MISMATCH",access.validate(other,ticket.getQrPayload()).result().code());assertNull(tickets.findById(ticket.getTicketId()).orElseThrow().getValidatedAt());
 }
 @Test void annulledAndRefundedTicketsNeverAdmit(){
  statuses.updateOne(ticket.getTicketId(),order,TicketStatus.ANULADA);assertEquals("TICKET_NOT_ENABLED",access.validate(event,ticket.getQrPayload()).result().code());
  statuses.updateOne(ticket.getTicketId(),order,TicketStatus.REINTEGRADA);assertEquals(409,access.validate(event,ticket.getQrPayload()).status());assertNull(tickets.findById(ticket.getTicketId()).orElseThrow().getValidatedAt());
 }
 @Test void cancellationGateAndCancelledBatchNeverAdmit(){
  gates.cancel(event);assertEquals("EVENT_BLOCKED",access.validate(event,ticket.getQrPayload()).result().code());
 }
 @Test void orderAnnulmentIsPreserved(){annulment.annul(order);assertEquals("TICKET_NOT_ENABLED",access.validate(event,ticket.getQrPayload()).result().code());assertNull(tickets.findById(ticket.getTicketId()).orElseThrow().getValidatedAt());}
 @Test void financialChangesCannotResetFirstAdmission(){
  access.validate(event,ticket.getQrPayload());var first=tickets.findById(ticket.getTicketId()).orElseThrow().getValidatedAt();
  statuses.updateOne(ticket.getTicketId(),order,TicketStatus.ANULADA);statuses.updateOne(ticket.getTicketId(),order,TicketStatus.REINTEGRADA);
  var stored=tickets.findById(ticket.getTicketId()).orElseThrow();assertEquals(first,stored.getValidatedAt());assertEquals(10L,stored.getValidatedBy());assertEquals(409,access.validate(event,ticket.getQrPayload()).status());
 }
 @Test void roleAssignmentAndRevocationAreRequired(){
  actor(11,"PERSONAL_ACCESO");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->access.validate(event,ticket.getQrPayload()));
  actor(1,"ADMINISTRADOR");access.assign(event,10L,false);actor(10,"PERSONAL_ACCESO");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->access.validate(event,ticket.getQrPayload()));
  actor(10,"CLIENTE");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->access.validate(event,ticket.getQrPayload()));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->access.assign(event,10L,true));
 }
 @Test void assignmentIsIdempotentAndDoesNotRequireRemoteIdentityCalls(){actor(1,"ADMINISTRADOR");var original=access.assign(event,10L,true);assertEquals(original,access.assign(event,10L,true));assertEquals(1,assignments.findAll().stream().filter(a->a.eventId.equals(event)).count());}
 @Test void twoConcurrentScansProduceExactlyOneAdmission()throws Exception{
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var calls=new ArrayList<Future<AdmissionService.Decision>>();for(int i=0;i<2;i++)calls.add(pool.submit(()->{start.await();actor(10,"PERSONAL_ACCESO");try{return access.validate(event,ticket.getQrPayload());}finally{SecurityContextHolder.clearContext();}}));start.countDown();var results=List.of(calls.get(0).get(15,TimeUnit.SECONDS),calls.get(1).get(15,TimeUnit.SECONDS));assertEquals(1,results.stream().filter(r->r.result().accepted()).count());assertEquals(1,results.stream().filter(r->r.status()==409&&r.result().code().equals("ALREADY_USED")).count());assertNotNull(tickets.findById(ticket.getTicketId()).orElseThrow().getValidatedAt());}finally{pool.shutdownNow();}
 }
 @Test void cancellationCommittedBeforeScanCannotAdmit()throws Exception{
  var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var cancel=pool.submit(()->new TransactionTemplate(manager).execute(tx->{gates.cancel(event);held.countDown();try{assertTrue(release.await(8,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}return null;}));assertTrue(held.await(5,TimeUnit.SECONDS));
   var scan=pool.submit(()->{actor(10,"PERSONAL_ACCESO");try{return access.validate(event,ticket.getQrPayload());}finally{SecurityContextHolder.clearContext();}});assertThrows(TimeoutException.class,()->scan.get(100,TimeUnit.MILLISECONDS));release.countDown();cancel.get(10,TimeUnit.SECONDS);assertEquals("EVENT_BLOCKED",scan.get(10,TimeUnit.SECONDS).result().code());assertNull(tickets.findById(ticket.getTicketId()).orElseThrow().getValidatedAt());
  }finally{release.countDown();pool.shutdownNow();}
 }
 String token(long id,String role)throws Exception{return "Bearer "+SecurityTestKeys.token("entradas","user",String.valueOf(id),role,null,300);}
 @Test void httpSecurityUsesRealJwtAndNeverDeclaredStaffId()throws Exception{
  String path="/accesos/eventos/"+event+"/validaciones",json="{\"qr\":\""+ticket.getQrPayload()+"\"}";
  mvc.perform(post(path).contentType("application/json").content(json)).andExpect(status().isUnauthorized());
  mvc.perform(post(path).header("Authorization",token(10,"CLIENTE")).contentType("application/json").content(json)).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Authorization",token(11,"PERSONAL_ACCESO")).header("X-User-Id","10").contentType("application/json").content(json)).andExpect(status().isForbidden());
  mvc.perform(put("/admin/accesos/eventos/"+event+"/personal/11").header("Authorization",token(10,"PERSONAL_ACCESO"))).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Authorization",token(10,"PERSONAL_ACCESO")).contentType("application/json").content(json)).andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
  mvc.perform(post(path).header("Authorization",token(10,"PERSONAL_ACCESO")).contentType("application/json").content(json)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_USED"));
 }
}
