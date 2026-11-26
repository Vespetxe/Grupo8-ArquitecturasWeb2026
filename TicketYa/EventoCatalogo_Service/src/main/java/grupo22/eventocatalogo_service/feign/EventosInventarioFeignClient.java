package grupo22.eventocatalogo_service.feign;
import org.springframework.web.bind.annotation.*;
@org.springframework.cloud.openfeign.FeignClient(name="EventosInventarioFeignClient",url="${ticketya.inventario.base-url:http://localhost:8082}",configuration=TargetFeignConfiguration.Inventario.class)
public interface EventosInventarioFeignClient {
 @PostMapping("/eventos/{id}/inventario") tools.jackson.databind.JsonNode initialize(@PathVariable("id") Long id,@RequestBody grupo22.eventocatalogo_service.service.EventWorkflow.StockSnapshot stock);
 @PostMapping("/internal/eventos/{id}/publicar") void publish(@PathVariable("id") Long id);
 @PostMapping("/internal/eventos/{id}/suspender") void suspend(@PathVariable("id") Long id);
 @PostMapping("/internal/eventos/{id}/habilitar") void enable(@PathVariable("id") Long id);
 @PostMapping("/eventos/{id}/cancelar-inventario") void cancel(@PathVariable("id") Long id);
 @GetMapping("/internal/reports/events/{id}/inventory") grupo22.eventocatalogo_service.reports.OrganizerReportController.Inventory report(@PathVariable("id") Long id);
}
