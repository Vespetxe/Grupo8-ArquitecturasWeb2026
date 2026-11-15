package ar.edu.unicen.grupo8.comprasservice.controller;
import ar.edu.unicen.grupo8.comprasservice.repository.CompraRepository;
import ar.edu.unicen.grupo8.comprasservice.entity.enums.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@RestController
public class RecommendationHistoryController {
 private final CompraRepository purchases;
 public RecommendationHistoryController(CompraRepository purchases){this.purchases=purchases;}
 public record History(List<Long> eventIds){}
 @GetMapping("/internal/recommendations/customers/{owner}/history") @Transactional(readOnly=true)
 public History history(@PathVariable Long owner){
  if(owner==null||owner<=0)throw new IllegalArgumentException("Invalid identity");
  return new History(purchases.recommendationEvents(owner,List.of(EstadoCompra.COMPLETADA,EstadoCompra.DEVUELTA_PARCIAL),List.of(EstadoPago.APROBADO,EstadoPago.REINTEGRADO_PARCIAL)));
 }
}
