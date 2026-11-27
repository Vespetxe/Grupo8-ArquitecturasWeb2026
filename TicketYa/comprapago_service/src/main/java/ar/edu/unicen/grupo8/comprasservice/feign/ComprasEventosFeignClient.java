package ar.edu.unicen.grupo8.comprasservice.feign;
import org.springframework.web.bind.annotation.*;
@org.springframework.cloud.openfeign.FeignClient(name="ComprasEventosFeignClient",url="${ticketya.eventos.base-url:http://localhost:8081}",configuration=TargetFeignConfiguration.Eventos.class)
public interface ComprasEventosFeignClient {
 @GetMapping("/api/v1/events/{id}") ar.edu.unicen.grupo8.comprasservice.client.FeignEventoAdapter.Evento get(@PathVariable("id") Long id);
}
