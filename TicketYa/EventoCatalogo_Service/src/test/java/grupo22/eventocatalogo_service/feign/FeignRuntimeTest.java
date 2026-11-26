package grupo22.eventocatalogo_service.feign;
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers=grupo22.eventocatalogo_service.SecurityTestKeys.class)
class FeignRuntimeTest {
 @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext context;
 @org.junit.jupiter.api.Test void productionInterfacesAreSpringManagedFeignProxies(){
  for(Class<?> type:java.util.List.of(EventosInventarioFeignClient.class,EventosEntradasFeignClient.class,EventosComprasFeignClient.class)){
   var proxy=context.getBean(type);
   org.junit.jupiter.api.Assertions.assertNotNull(type.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class));
   org.junit.jupiter.api.Assertions.assertTrue(java.lang.reflect.Proxy.isProxyClass(proxy.getClass()));
   org.junit.jupiter.api.Assertions.assertTrue(java.lang.reflect.Proxy.getInvocationHandler(proxy).getClass().getName().toLowerCase().contains("feign"));
  }
 }
}
