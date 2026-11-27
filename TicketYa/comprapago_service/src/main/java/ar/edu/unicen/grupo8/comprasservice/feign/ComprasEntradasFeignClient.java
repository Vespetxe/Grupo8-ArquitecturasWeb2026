package ar.edu.unicen.grupo8.comprasservice.feign;
import org.springframework.web.bind.annotation.*;
@org.springframework.cloud.openfeign.FeignClient(name="ComprasEntradasFeignClient",url="${ticketya.entradas.base-url:http://localhost:8083}",configuration=TargetFeignConfiguration.Entradas.class)
public interface ComprasEntradasFeignClient {
 @GetMapping("/tickets") ar.edu.unicen.grupo8.comprasservice.client.FeignEntradaAdapter.Ticket[] get(@RequestParam("orderId") Long id);
 @PostMapping("/internal/tickets/issue") void issue(@RequestBody java.util.Map<String,String> body);
 @PostMapping("/internal/tickets/orders/{id}/annul") void annul(@PathVariable("id") Long id);
 @PatchMapping("/internal/tickets/{id}/status") void status(@PathVariable("id") String id,@RequestBody java.util.Map<String,String> body);
}
