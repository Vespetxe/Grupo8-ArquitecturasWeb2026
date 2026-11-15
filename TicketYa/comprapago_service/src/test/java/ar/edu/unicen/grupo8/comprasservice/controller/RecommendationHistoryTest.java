package ar.edu.unicen.grupo8.comprasservice.controller;
import ar.edu.unicen.grupo8.comprasservice.entity.*;
import ar.edu.unicen.grupo8.comprasservice.entity.enums.*;
import ar.edu.unicen.grupo8.comprasservice.repository.*;
import ar.edu.unicen.grupo8.comprasservice.SecurityTestKeys;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class RecommendationHistoryTest {
 @Autowired CompraRepository purchases;
 @Autowired PagoRepository payments;
 @Autowired RecommendationHistoryController controller;
 @Autowired MockMvc mvc;
 static final AtomicLong IDS=new AtomicLong(900000);
 static final Long OWNER=811111L;
 Compra purchase(Long owner,long event,EstadoCompra state,EstadoPago payment,int refunded){
  Compra c=new Compra();c.setIdentityOwnerId(owner);c.setClienteId(77L);c.setEventoId(event);c.setReservaId(IDS.incrementAndGet());c.setImporteTotal(BigDecimal.TEN);ReflectionTestUtils.setField(c,"estado",state);c=purchases.saveAndFlush(c);
  if(payment!=null){Pago p=new Pago();p.setCompra(c);p.setClaveIdempotencia("history-test-"+UUID.randomUUID());p.setImporte(BigDecimal.TEN);p.setImporteReintegrado(BigDecimal.valueOf(refunded));ReflectionTestUtils.setField(p,"estado",payment);payments.saveAndFlush(p);}return c;
 }
 @Test void onlyEffectiveCompletedAndPartialPurchasesOfIdentityOwner(){
  purchase(OWNER,10,EstadoCompra.COMPLETADA,EstadoPago.APROBADO,0);purchase(OWNER,20,EstadoCompra.DEVUELTA_PARCIAL,EstadoPago.REINTEGRADO_PARCIAL,5);purchase(OWNER,10,EstadoCompra.COMPLETADA,EstadoPago.APROBADO,0);
  purchase(OWNER+1,30,EstadoCompra.COMPLETADA,EstadoPago.APROBADO,0);purchase(null,40,EstadoCompra.COMPLETADA,EstadoPago.APROBADO,0);
  assertEquals(List.of(10L,20L),controller.history(OWNER).eventIds());
 }
 @Test void failedPendingRefundedAndUnknownAmountsAreExcluded(){
  long event=100;for(EstadoCompra state:EstadoCompra.values())if(state!=EstadoCompra.COMPLETADA&&state!=EstadoCompra.DEVUELTA_PARCIAL)purchase(OWNER,event++,state,EstadoPago.APROBADO,0);
  for(EstadoPago state:EstadoPago.values())if(state!=EstadoPago.APROBADO&&state!=EstadoPago.REINTEGRADO_PARCIAL)purchase(OWNER,event++,EstadoCompra.COMPLETADA,state,0);
  purchase(OWNER,event++,EstadoCompra.COMPLETADA,null,0);purchase(OWNER,event++,EstadoCompra.DEVUELTA_PARCIAL,EstadoPago.REINTEGRADO_PARCIAL,10);
  var cancelling=purchase(OWNER,event++,EstadoCompra.COMPLETADA,EstadoPago.APROBADO,0);cancelling.solicitarCancelacionEvento();purchases.saveAndFlush(cancelling);
  assertTrue(controller.history(OWNER).eventIds().isEmpty());
 }
 String technical(String caller,String scope)throws Exception{return "Bearer "+SecurityTestKeys.token("compras","service","service:"+caller+"-to-compras",null,scope,300);}
 @Test void onlyEventsWithMinimalReadScopeCanRead()throws Exception{
  String path="/internal/recommendations/customers/"+OWNER+"/history";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).header("Authorization",technical("eventos","purchases.history.read"))).andExpect(status().isOk()).andExpect(jsonPath("$.eventIds").isArray());
  mvc.perform(get(path).header("Authorization",technical("eventos","purchases.event-cancel"))).andExpect(status().isForbidden());
  mvc.perform(get(path).header("Authorization",technical("entradas","purchases.history.read"))).andExpect(status().isForbidden());
  mvc.perform(get(path).header("Authorization","Bearer "+SecurityTestKeys.token("compras","user",OWNER.toString(),"CLIENTE",null,300))).andExpect(status().isForbidden());
  mvc.perform(post("/admin/eventos/1/cancelacion").header("Authorization",technical("eventos","purchases.history.read")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
}
