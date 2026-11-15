package ar.edu.unicen.grupo8.comprasservice.reports;
import ar.edu.unicen.grupo8.comprasservice.entity.*;
import ar.edu.unicen.grupo8.comprasservice.entity.enums.*;
import ar.edu.unicen.grupo8.comprasservice.repository.*;
import ar.edu.unicen.grupo8.comprasservice.SecurityTestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class SalesReportTest {
 @Autowired SalesReportController reports;@Autowired CompraRepository purchases;@Autowired PagoRepository payments;@Autowired ReintegroRepository refunds;@Autowired DevolucionRepository returns;@Autowired DetalleDevolucionRepository details;@Autowired MockMvc mvc;
 static final long EVENT=987610L;
 Pago purchase(long event,EstadoCompra state){Compra c=new Compra();c.setClienteId(1L);c.setEventoId(event);c.setReservaId(Math.abs(UUID.randomUUID().getMostSignificantBits()));c.setImporteTotal(new BigDecimal("30000"));ReflectionTestUtils.setField(c,"estado",state);c.setEtapa(EtapaCompra.FINALIZADA);DetalleCompra d=new DetalleCompra();d.setCompra(c);d.setSectorId(51L);d.setCantidad(2);d.setPrecioUnitario(new BigDecimal("15000"));d.setSubtotal(new BigDecimal("30000"));c.getDetalles().add(d);purchases.saveAndFlush(c);Pago p=new Pago();p.setCompra(c);p.setClaveIdempotencia(UUID.randomUUID().toString());p.setImporte(new BigDecimal("30000"));ReflectionTestUtils.setField(p,"estado",EstadoPago.APROBADO);return payments.saveAndFlush(p);}
 void refund(Pago p,int count,EstadoReintegro state){Devolucion d=new Devolucion();d.setCompra(p.getCompra());d.setMotivo("Report test");returns.saveAndFlush(d);for(int n=0;n<count;n++){DetalleDevolucion item=new DetalleDevolucion();item.setDevolucion(d);item.setDetalleCompra(p.getCompra().getDetalles().get(0));item.setEntradaId(10001L+n);item.setImporte(new BigDecimal("15000"));details.saveAndFlush(item);}Reintegro r=new Reintegro();r.setPago(p);r.setDevolucion(d);r.setClaveIdempotencia(UUID.randomUUID().toString());r.setImporte(new BigDecimal(15000*count));ReflectionTestUtils.setField(r,"estado",state);refunds.saveAndFlush(r);if(state==EstadoReintegro.COMPLETADO){p.setImporteReintegrado(p.getImporteReintegrado().add(r.getImporte()));payments.saveAndFlush(p);}}
 @Test void completedSalesAreGroupedOnceAndOtherEventsExcluded(){purchase(EVENT,EstadoCompra.COMPLETADA);purchase(EVENT+1,EstadoCompra.COMPLETADA);var a=reports.report(EVENT).sectors().get(0);assertEquals(2,a.soldTickets());assertEquals(0,a.returnedTickets());assertEquals(0,new BigDecimal("30000").compareTo(a.netCollected()));assertEquals(a,reports.report(EVENT).sectors().get(0));}
 @Test void partialRefundCountsOnlyItsTicketAndAmount(){var p=purchase(EVENT,EstadoCompra.DEVUELTA_PARCIAL);ReflectionTestUtils.setField(p,"estado",EstadoPago.REINTEGRADO_PARCIAL);refund(p,1,EstadoReintegro.COMPLETADO);var a=reports.report(EVENT).sectors().get(0);assertEquals(2,a.soldTickets());assertEquals(1,a.returnedTickets());assertEquals(0,new BigDecimal("15000").compareTo(a.netCollected()));}
 @Test void fullRefundPreservesGrossSalesButNetIsZero(){var p=purchase(EVENT,EstadoCompra.DEVUELTA_TOTAL);ReflectionTestUtils.setField(p,"estado",EstadoPago.REINTEGRADO_TOTAL);refund(p,2,EstadoReintegro.COMPLETADO);var a=reports.report(EVENT).sectors().get(0);assertEquals(2,a.soldTickets());assertEquals(2,a.returnedTickets());assertEquals(0,BigDecimal.ZERO.compareTo(a.netCollected()));}
 @Test void emptyPendingFailedAndCancelledPurchasesAreNotSales(){for(var state:List.of(EstadoCompra.PENDIENTE,EstadoCompra.FALLIDA,EstadoCompra.CANCELADA))purchase(EVENT,state);assertTrue(reports.report(EVENT).sectors().isEmpty());assertTrue(reports.report(EVENT+2).sectors().isEmpty());}
 @Test void uncertainRefundIsExplicitAndCannotInventNetRevenue(){var p=purchase(EVENT,EstadoCompra.COMPLETADA);refund(p,1,EstadoReintegro.RESULTADO_DESCONOCIDO);var report=reports.report(EVENT);assertFalse(report.financialComplete());assertNull(report.sectors().get(0).netCollected());assertEquals(0,report.sectors().get(0).returnedTickets());}
 @Test void inconsistentChargeCannotBeReportedAsSuccess(){var p=purchase(EVENT,EstadoCompra.COMPLETADA);p.setImporte(BigDecimal.ONE);payments.saveAndFlush(p);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->reports.report(EVENT));}
 @Test void technicalReadRequiresEventsAndItsSpecificScope()throws Exception{String url="/internal/reports/events/"+EVENT+"/sales";mvc.perform(get(url)).andExpect(status().isUnauthorized());for(String scope:List.of("purchases.history.read","purchases.event-cancel"))mvc.perform(get(url).header("Authorization",token("eventos",scope))).andExpect(status().isForbidden());mvc.perform(get(url).header("Authorization",token("entradas","reports.sales.read"))).andExpect(status().isForbidden());mvc.perform(get(url).header("Authorization",token("eventos","reports.sales.read"))).andExpect(status().isOk());mvc.perform(post("/admin/eventos/1/cancelacion").header("Authorization",token("eventos","reports.sales.read")).contentType("application/json").content("{}")).andExpect(status().isForbidden());}
 String token(String caller,String scope)throws Exception{return "Bearer "+SecurityTestKeys.token("compras","service","service:"+caller+"-to-compras",null,scope,300);}
 @Test void missingRefundLedgerCannotInventNetRevenue(){var p=purchase(EVENT,EstadoCompra.DEVUELTA_PARCIAL);p.setImporteReintegrado(new BigDecimal("15000"));ReflectionTestUtils.setField(p,"estado",EstadoPago.REINTEGRADO_PARCIAL);payments.saveAndFlush(p);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->reports.report(EVENT));}
}
