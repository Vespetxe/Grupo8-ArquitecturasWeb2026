package ar.edu.unicen.grupo8.comprasservice.feign;
@org.springframework.test.context.ActiveProfiles("integracion")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers=ar.edu.unicen.grupo8.comprasservice.SecurityTestKeys.class)
class FeignRuntimeTest {
 @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext context;
 @org.junit.jupiter.api.Test void realAdaptersUseTheSpringFeignBeans(){
  for(var entry:java.util.Map.of(ar.edu.unicen.grupo8.comprasservice.client.FeignEventoAdapter.class,ComprasEventosFeignClient.class,ar.edu.unicen.grupo8.comprasservice.client.FeignReservaAdapter.class,ComprasInventarioFeignClient.class,ar.edu.unicen.grupo8.comprasservice.client.FeignEntradaAdapter.class,ComprasEntradasFeignClient.class).entrySet()){
   var adapter=context.getBean(entry.getKey());org.junit.jupiter.api.Assertions.assertNotNull(adapter);
   String name=java.beans.Introspector.decapitalize(entry.getKey().getSimpleName());
   org.junit.jupiter.api.Assertions.assertSame(adapter,context.getBean(name));
   for(Class<?> businessPort:entry.getKey().getInterfaces())org.junit.jupiter.api.Assertions.assertSame(adapter,context.getBean(businessPort));
   org.junit.jupiter.api.Assertions.assertEquals(1,java.util.Arrays.stream(entry.getKey().getDeclaredConstructors()).filter(c->c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)).count());
  }
 }
 @org.junit.jupiter.api.Test void historicalAdapterBeanNamesAreAbsent(){
  for(String name:java.util.List.of("restEventoClient","restReservaClient","restEntradaClient"))org.junit.jupiter.api.Assertions.assertFalse(context.containsBean(name));
 }
 @org.junit.jupiter.api.Test void productionInterfacesAreSpringManagedFeignProxies(){
  for(Class<?> type:java.util.List.of(ComprasEventosFeignClient.class,ComprasInventarioFeignClient.class,ComprasEntradasFeignClient.class)){
   var proxy=context.getBean(type);
   org.junit.jupiter.api.Assertions.assertNotNull(type.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class));
   org.junit.jupiter.api.Assertions.assertTrue(java.lang.reflect.Proxy.isProxyClass(proxy.getClass()));
   org.junit.jupiter.api.Assertions.assertTrue(java.lang.reflect.Proxy.getInvocationHandler(proxy).getClass().getName().toLowerCase().contains("feign"));
  }
 }
}
