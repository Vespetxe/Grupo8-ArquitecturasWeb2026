package grupo22.eventocatalogo_service.feign;
import org.springframework.web.bind.annotation.*;
@org.springframework.cloud.openfeign.FeignClient(name="EventosComprasFeignClient",url="${ticketya.compras.base-url:http://localhost:8084}",configuration=TargetFeignConfiguration.Compras.class)
public interface EventosComprasFeignClient {
 @PostMapping("/admin/eventos/{id}/cancelacion") org.springframework.http.ResponseEntity<tools.jackson.databind.JsonNode> cancel(@PathVariable("id") Long id);
 @GetMapping("/internal/recommendations/customers/{id}/history") grupo22.eventocatalogo_service.recommendations.PurchaseHistoryClient.History history(@PathVariable("id") Long id);
 @PostMapping("/internal/notifications/events") grupo22.eventocatalogo_service.notifications.EventNoticeDispatcher.Ack notify(@RequestBody grupo22.eventocatalogo_service.notifications.EventNoticeDispatcher.Delivery body);
 @GetMapping("/internal/reports/events/{id}/sales") grupo22.eventocatalogo_service.reports.OrganizerReportController.Sales report(@PathVariable("id") Long id);
}
