package ar.edu.tudai.ticketya.servicio_entradas.feign;
@org.springframework.context.annotation.Configuration
@org.springframework.cloud.openfeign.EnableFeignClients(clients={EntradasComprasFeignClient.class})
public class FeignClientsConfiguration {}
