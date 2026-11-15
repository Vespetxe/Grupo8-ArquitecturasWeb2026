package ar.edu.unicen.grupo8.comprasservice.reports;
import ar.edu.unicen.grupo8.comprasservice.entity.*;
import ar.edu.unicen.grupo8.comprasservice.entity.enums.*;
import ar.edu.unicen.grupo8.comprasservice.repository.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@RestController
public class SalesReportController {
 private final CompraRepository purchases;private final PagoRepository payments;private final ReintegroRepository refunds;private final DetalleDevolucionRepository returned;
 public SalesReportController(CompraRepository purchases,PagoRepository payments,ReintegroRepository refunds,DetalleDevolucionRepository returned){this.purchases=purchases;this.payments=payments;this.refunds=refunds;this.returned=returned;}
 public record Sector(Long sectorId,long soldTickets,long returnedTickets,BigDecimal grossCollected,BigDecimal refundedAmount,BigDecimal netCollected){}
 public record Snapshot(Long eventId,Instant generatedAt,boolean financialComplete,List<Sector> sectors){}
 static class Amounts {long sold;Set<Long> returned=new HashSet<>();BigDecimal gross=BigDecimal.ZERO,refund=BigDecimal.ZERO;}
 private void require(boolean ok){if(!ok)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Financial report source requires reconciliation");}
 @GetMapping("/internal/reports/events/{eventId}/sales") @Transactional(readOnly=true)
 public Snapshot report(@PathVariable Long eventId){
  if(eventId<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid event");
  Map<Long,Amounts> sectors=new TreeMap<>();boolean complete=true;Set<Long> refundedTickets=new HashSet<>();
  for(var purchase:purchases.findByEventoId(eventId)){
   var allPayments=payments.findByCompraId(purchase.getId());
   if(purchase.getEstado()==EstadoCompra.PROCESANDO)complete=false;
   for(var payment:allPayments){if(payment.getEstado()==EstadoPago.RESULTADO_DESCONOCIDO||payment.getEstado()==EstadoPago.REINTEGRO_DESCONOCIDO)complete=false;for(var refund:refunds.findByPagoId(payment.getId()))if(refund.getEstado()==EstadoReintegro.PENDIENTE||refund.getEstado()==EstadoReintegro.RESULTADO_DESCONOCIDO)complete=false;}
   boolean effective=Set.of(EstadoCompra.COMPLETADA,EstadoCompra.DEVUELTA_PARCIAL,EstadoCompra.DEVUELTA_TOTAL).contains(purchase.getEstado())&&purchase.getEtapa()==EtapaCompra.FINALIZADA;
   if(!effective&&allPayments.stream().anyMatch(p->Set.of(EstadoPago.APROBADO,EstadoPago.REINTEGRADO_PARCIAL,EstadoPago.REINTEGRO_DESCONOCIDO).contains(p.getEstado())&&p.getImporte().compareTo(p.getImporteReintegrado()==null?BigDecimal.ZERO:p.getImporteReintegrado())>0))complete=false;
   if(!Set.of(EstadoCompra.COMPLETADA,EstadoCompra.DEVUELTA_PARCIAL,EstadoCompra.DEVUELTA_TOTAL).contains(purchase.getEstado())||purchase.getEtapa()!=EtapaCompra.FINALIZADA)continue;
   var paid=allPayments.stream().filter(p->Set.of(EstadoPago.APROBADO,EstadoPago.REINTEGRADO_PARCIAL,EstadoPago.REINTEGRADO_TOTAL,EstadoPago.REINTEGRO_DESCONOCIDO).contains(p.getEstado())).toList();require(paid.size()==1);var payment=paid.get(0);
   BigDecimal gross=BigDecimal.ZERO;require(!purchase.getDetalles().isEmpty());
   for(var detail:purchase.getDetalles()){
    require(detail.getSectorId()!=null&&detail.getSectorId()>0&&detail.getCantidad()!=null&&detail.getCantidad()>0&&detail.getPrecioUnitario()!=null&&detail.getSubtotal()!=null&&detail.getPrecioUnitario().signum()>=0&&detail.getSubtotal().compareTo(detail.getPrecioUnitario().multiply(BigDecimal.valueOf(detail.getCantidad())))==0);
    var amount=sectors.computeIfAbsent(detail.getSectorId(),key->new Amounts());amount.sold+=detail.getCantidad();amount.gross=amount.gross.add(detail.getSubtotal());gross=gross.add(detail.getSubtotal());
   }
   require(gross.compareTo(payment.getImporte())==0&&gross.compareTo(purchase.getImporteTotal())==0);
   BigDecimal confirmedRefund=BigDecimal.ZERO;
   for(var refund:refunds.findByPagoId(payment.getId()))if(refund.getEstado()==EstadoReintegro.COMPLETADO){
    require(refund.getDevolucion()!=null);var details=returned.findByDevolucionId(refund.getDevolucion().getId());BigDecimal refunded=BigDecimal.ZERO;require(!details.isEmpty());
    for(var detail:details){require(detail.getDetalleCompra().getCompra().getId().equals(purchase.getId())&&detail.getEntradaId()!=null&&refundedTickets.add(detail.getEntradaId())&&detail.getImporte()!=null&&detail.getImporte().signum()>=0);var amount=sectors.get(detail.getDetalleCompra().getSectorId());require(amount!=null);amount.returned.add(detail.getEntradaId());amount.refund=amount.refund.add(detail.getImporte());refunded=refunded.add(detail.getImporte());}
    require(refunded.compareTo(refund.getImporte())==0);confirmedRefund=confirmedRefund.add(refund.getImporte());
   }
   require(confirmedRefund.compareTo(payment.getImporteReintegrado()==null?BigDecimal.ZERO:payment.getImporteReintegrado())==0);
  }
  final boolean settled=complete;List<Sector> result=new ArrayList<>();for(var entry:sectors.entrySet()){var a=entry.getValue();require(a.returned.size()<=a.sold&&a.refund.compareTo(a.gross)<=0);result.add(new Sector(entry.getKey(),a.sold,a.returned.size(),a.gross,a.refund,settled?a.gross.subtract(a.refund):null));}
  return new Snapshot(eventId,Instant.now(),complete,List.copyOf(result));
 }
}
