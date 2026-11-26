package ar.edu.unicen.grupo8.comprasservice.feign;
import org.springframework.web.bind.annotation.*;
@org.springframework.cloud.openfeign.FeignClient(name="ComprasInventarioFeignClient",url="${ticketya.inventario.base-url:http://localhost:8082}",configuration=TargetFeignConfiguration.Inventario.class)
public interface ComprasInventarioFeignClient {
 @GetMapping("/reservas/{id}") ar.edu.unicen.grupo8.comprasservice.client.RestReservaClient.Reserva get(@PathVariable("id") Long id);
 @PostMapping("/reservas/{id}/confirmar") void confirm(@PathVariable("id") Long id);
 @PostMapping("/reservas/{id}/liberar") void release(@PathVariable("id") Long id);
 @PostMapping("/internal/reservas/{id}/compensar") void compensate(@PathVariable("id") Long id);
 @PostMapping("/internal/reservas/{id}/devoluciones") void giveBack(@PathVariable("id") Long id,@RequestBody Object body);
 @GetMapping("/internal/reservas/{id}/devoluciones") String[] returned(@PathVariable("id") Long id);
}
