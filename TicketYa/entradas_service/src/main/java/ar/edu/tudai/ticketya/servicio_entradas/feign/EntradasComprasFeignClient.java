package ar.edu.tudai.ticketya.servicio_entradas.feign;
import org.springframework.web.bind.annotation.*;
@org.springframework.cloud.openfeign.FeignClient(name="EntradasComprasFeignClient",url="${ticketya.compras.base-url:http://localhost:8084}",configuration=TargetFeignConfiguration.Compras.class)
public interface EntradasComprasFeignClient {
 @GetMapping("/internal/orders/{id}") ar.edu.tudai.ticketya.servicio_entradas.integration.OrderSnapshot get(@PathVariable("id") String id);
}
