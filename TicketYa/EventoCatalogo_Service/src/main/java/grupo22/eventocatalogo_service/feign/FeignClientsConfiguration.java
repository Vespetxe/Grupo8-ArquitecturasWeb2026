package grupo22.eventocatalogo_service.feign;
@org.springframework.context.annotation.Configuration
@org.springframework.cloud.openfeign.EnableFeignClients(clients={EventosInventarioFeignClient.class,EventosEntradasFeignClient.class,EventosComprasFeignClient.class})
public class FeignClientsConfiguration {}
