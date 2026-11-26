package ar.edu.unicen.grupo8.comprasservice.feign;
@org.springframework.context.annotation.Configuration
@org.springframework.cloud.openfeign.EnableFeignClients(clients={ComprasEventosFeignClient.class,ComprasInventarioFeignClient.class,ComprasEntradasFeignClient.class})
public class FeignClientsConfiguration {}
