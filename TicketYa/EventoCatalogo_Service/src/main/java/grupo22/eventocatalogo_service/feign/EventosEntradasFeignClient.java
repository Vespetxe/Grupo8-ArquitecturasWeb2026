package grupo22.eventocatalogo_service.feign;
import org.springframework.web.bind.annotation.*;
@org.springframework.cloud.openfeign.FeignClient(name="EventosEntradasFeignClient",url="${ticketya.entradas.base-url:http://localhost:8083}",configuration=TargetFeignConfiguration.Entradas.class)
public interface EventosEntradasFeignClient {
 @PostMapping("/internal/events/{id}/cancel-emission") void block(@PathVariable("id") Long id);
 @GetMapping("/internal/reports/events/{id}/tickets") grupo22.eventocatalogo_service.reports.OrganizerReportController.Tickets report(@PathVariable("id") Long id);
}
