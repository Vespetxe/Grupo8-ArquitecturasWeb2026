package ar.edu.unicen.grupo8.comprasservice.notifications;
import ar.edu.unicen.grupo8.comprasservice.SecurityTestKeys;
import ar.edu.unicen.grupo8.comprasservice.entity.*;
import ar.edu.unicen.grupo8.comprasservice.entity.enums.*;
import ar.edu.unicen.grupo8.comprasservice.repository.*;
import ar.edu.unicen.grupo8.comprasservice.service.PagoRegistroService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class NotificationTest {
 @Autowired NotificationWriter writer;@MockitoSpyBean NotificationRepository repository;
 @Autowired MockMvc mvc;@Autowired PlatformTransactionManager manager;@Autowired CompraRepository purchases;@Autowired PagoRepository payments;@Autowired PagoRegistroService registry;
 TransactionTemplate tx;static final Long OWNER=865001L;
 @BeforeEach void before(){tx=new TransactionTemplate(manager);repository.deleteAll();}
 @AfterEach void after(){reset(repository);}
 String token(long owner,String role)throws Exception{return "Bearer "+SecurityTestKeys.token("compras","user",String.valueOf(owner),role,null,300);}
 @Test void persistentIdempotentInboxAndOwnedRead()throws Exception{
  tx.executeWithoutResult(s->{writer.record(OWNER,"COMPRA_CONFIRMADA","COMPRA",71L,"test:71",Instant.now());writer.record(OWNER,"COMPRA_CONFIRMADA","COMPRA",71L,"test:71",Instant.now());});assertEquals(1,repository.count());String id=repository.findAll().get(0).id;
  mvc.perform(get("/api/v1/me/notifications?userId="+OWNER).header("Authorization",token(OWNER+1,"CLIENTE"))).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization",token(OWNER,"CLIENTE"))).andExpect(jsonPath("$.unread").value(1));
  mvc.perform(put("/api/v1/me/notifications/"+id+"/read").header("Authorization",token(OWNER+1,"CLIENTE"))).andExpect(status().isNotFound());
  for(int i=0;i<2;i++)mvc.perform(put("/api/v1/me/notifications/"+id+"/read").header("Authorization",token(OWNER,"CLIENTE"))).andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
  assertEquals(0,repository.countByOwnerIdAndReadAtIsNull(OWNER));assertEquals(1,repository.count());
 }
 @Test void authenticationRolesAndPagination()throws Exception{
  mvc.perform(get("/api/v1/me/notifications")).andExpect(status().isUnauthorized());
  for(String role:List.of("ORGANIZADOR","ADMINISTRADOR","PERSONAL_ACCESO"))mvc.perform(get("/api/v1/me/notifications").header("Authorization",token(OWNER,role))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/me/notifications?size=101").header("Authorization",token(OWNER,"CLIENTE"))).andExpect(status().isBadRequest());
 }
 @Test void failedPendingAndUnconfirmedEmissionNeverProduceSuccess(){
  Compra c=new Compra();c.setId(901L);c.setIdentityOwnerId(OWNER);c.setReservaId(72L);Pago p=new Pago();p.setId(902L);p.setCompra(c);
  tx.executeWithoutResult(s->writer.payment(c,p));ReflectionTestUtils.setField(p,"estado",EstadoPago.RECHAZADO);ReflectionTestUtils.setField(c,"estado",EstadoCompra.FALLIDA);c.setEtapa(EtapaCompra.FINALIZADA);tx.executeWithoutResult(s->writer.payment(c,p));assertEquals(0,repository.count());
  ReflectionTestUtils.setField(p,"estado",EstadoPago.APROBADO);ReflectionTestUtils.setField(c,"estado",EstadoCompra.PROCESANDO);c.setEtapa(EtapaCompra.EMISION);tx.executeWithoutResult(s->writer.payment(c,p));assertEquals(List.of("RESERVA_CONFIRMADA"),repository.findAll().stream().map(n->n.type).toList());
 }
 @Test void rollbackDoesNotPublishFalseNotification(){
  tx.executeWithoutResult(s->{writer.record(OWNER,"COMPRA_CONFIRMADA","COMPRA",73L,"rollback",Instant.now());s.setRollbackOnly();});assertEquals(0,repository.count());
 }
 @Test void notificationFailureRollsBackOnlyLocalStageAndSameIdsRecover(){
  Long id=tx.execute(s->{Compra c=new Compra();c.setIdentityOwnerId(OWNER);c.setClienteId(OWNER);c.setEventoId(1L);c.setReservaId(9865001L);c.setImporteTotal(BigDecimal.TEN);ReflectionTestUtils.setField(c,"estado",EstadoCompra.PROCESANDO);c.setEtapa(EtapaCompra.EMISION);c.setEjecutor("same-owner");c.setBloqueoHasta(LocalDateTime.now().plusMinutes(2));purchases.saveAndFlush(c);Pago p=new Pago();p.setCompra(c);p.setImporte(BigDecimal.TEN);p.setClaveIdempotencia("notification-recovery-"+UUID.randomUUID());ReflectionTestUtils.setField(p,"estado",EstadoPago.APROBADO);return payments.saveAndFlush(p).getId();});
  doThrow(new org.springframework.dao.DataAccessResourceFailureException("simulated inbox failure")).when(repository).insert(anyString(),anyLong(),anyString(),anyString(),anyLong(),any());
  assertThrows(RuntimeException.class,()->registry.cambiar(id,"same-owner",(c,p)->{c.setEstado(EstadoCompra.COMPLETADA);c.setEtapa(EtapaCompra.FINALIZADA);c.getEntradasIds().add(9865001L);}));
  assertEquals(EstadoCompra.PROCESANDO,registry.obtener(id).getCompra().getEstado());assertEquals(0,repository.count());reset(repository);
  registry.cambiar(id,"same-owner",(c,p)->{c.setEstado(EstadoCompra.COMPLETADA);c.setEtapa(EtapaCompra.FINALIZADA);c.getEntradasIds().add(9865001L);});
  assertEquals(id,registry.obtener(id).getId());assertEquals(3,repository.count());assertEquals(1,payments.findByCompraId(registry.obtener(id).getCompra().getId()).size());
 }
 @Test void concurrentDuplicateDeliveryCreatesOneRow()throws Exception{
  try(var workers=Executors.newFixedThreadPool(4)){var tasks=new ArrayList<Callable<Void>>();for(int n=0;n<8;n++)tasks.add(()->{tx.executeWithoutResult(s->writer.record(OWNER,"ENTRADAS_EMITIDAS","COMPRA",74L,"concurrent",Instant.now()));return null;});for(var result:workers.invokeAll(tasks))result.get();}assertEquals(1,repository.count());
 }
 @Test void refundAndCancellationOnlyAtTerminalStage(){
  Compra c=new Compra();c.setId(75L);c.setIdentityOwnerId(OWNER);ReflectionTestUtils.setField(c,"estado",EstadoCompra.CANCELADA);c.setEtapa(EtapaCompra.LIBERACION);tx.executeWithoutResult(s->writer.cancelled(c));assertEquals(0,repository.count());c.setEtapa(EtapaCompra.FINALIZADA);tx.executeWithoutResult(s->{writer.cancelled(c);writer.cancelled(c);});
  Devolucion d=new Devolucion();d.setId(76L);d.setCompra(c);ReflectionTestUtils.setField(d,"estado",EstadoDevolucion.PROCESANDO);tx.executeWithoutResult(s->writer.refunded(d));assertEquals(1,repository.count());ReflectionTestUtils.setField(d,"estado",EstadoDevolucion.COMPLETADA);d.setEtapa(EtapaDevolucion.FINALIZADA);tx.executeWithoutResult(s->{writer.refunded(d);writer.refunded(d);});assertEquals(2,repository.count());
 }
 @Test void eventDeliveryRequiresSpecificTechnicalScope()throws Exception{
  String path="/internal/notifications/events",body="{\"deliveryId\":1,\"eventId\":1,\"type\":\"ARTISTA_FAVORITO_PUBLICADO\",\"occurredAt\":\"2030-01-01T00:00:00Z\",\"recipients\":["+OWNER+"]}";
  for(String scope:List.of("purchases.history.read","purchases.event-cancel"))mvc.perform(post(path).header("Authorization","Bearer "+SecurityTestKeys.token("compras","service","service:eventos-to-compras",null,scope,300)).contentType("application/json").content(body)).andExpect(status().isForbidden());
  for(int i=0;i<2;i++)mvc.perform(post(path).header("Authorization","Bearer "+SecurityTestKeys.token("compras","service","service:eventos-to-compras",null,"notifications.publish",300)).contentType("application/json").content(body)).andExpect(status().isOk());assertEquals(1,repository.count());
  mvc.perform(post("/admin/eventos/1/cancelacion").header("Authorization","Bearer "+SecurityTestKeys.token("compras","service","service:eventos-to-compras",null,"notifications.publish",300)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
}
