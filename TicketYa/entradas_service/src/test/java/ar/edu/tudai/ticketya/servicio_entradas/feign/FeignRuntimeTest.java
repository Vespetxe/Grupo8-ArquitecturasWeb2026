package ar.edu.tudai.ticketya.servicio_entradas.feign;
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers=ar.edu.tudai.ticketya.servicio_entradas.SecurityTestKeys.class)
class FeignRuntimeTest {
 @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext context;
 @org.junit.jupiter.api.Test void renamedAdapterAndOAuthConfigurationKeepTheirSpringWiring(){
  var adapter=context.getBean(ar.edu.tudai.ticketya.servicio_entradas.integration.FeignComprasOrderAdapter.class);
  org.junit.jupiter.api.Assertions.assertSame(adapter,context.getBean("feignComprasOrderAdapter"));
  org.junit.jupiter.api.Assertions.assertSame(adapter,context.getBean(ar.edu.tudai.ticketya.servicio_entradas.integration.OrderClient.class));
  org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("restComprasOrderClient"));
  org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("restClientConfiguration"));
  org.junit.jupiter.api.Assertions.assertNotNull(context.getBean(ar.edu.tudai.ticketya.servicio_entradas.config.OAuth2RestClientConfiguration.class));
  org.junit.jupiter.api.Assertions.assertNotNull(context.getBean("technicalClient",org.springframework.boot.restclient.RestClientCustomizer.class));
 }
 @org.junit.jupiter.api.Test void productionInterfacesAreSpringManagedFeignProxies(){
  for(Class<?> type:java.util.List.of(EntradasComprasFeignClient.class)){
   var proxy=context.getBean(type);
   org.junit.jupiter.api.Assertions.assertNotNull(type.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class));
   org.junit.jupiter.api.Assertions.assertTrue(java.lang.reflect.Proxy.isProxyClass(proxy.getClass()));
   org.junit.jupiter.api.Assertions.assertTrue(java.lang.reflect.Proxy.getInvocationHandler(proxy).getClass().getName().toLowerCase().contains("feign"));
  }
 }
}
