package ar.edu.tudai.ticketya.servicio_entradas.security;
import jakarta.servlet.http.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.core.MethodParameter;
import java.lang.reflect.Type;
import ar.edu.tudai.ticketya.servicio_entradas.integration.*;
/** HTTP boundary checks run before controllers, including GETs with persistence side effects. */
@RestControllerAdvice
public class ResourceOwnership extends RequestBodyAdviceAdapter implements HandlerInterceptor, WebMvcConfigurer {
 private final OrderClient orders;
 public ResourceOwnership(OrderClient orders){this.orders=orders;}
 public boolean visibleOrder(String id){try{return CurrentActor.visible(orders.getOrderForOwnership(id).identityOwnerId());}catch(OrderNotFoundException e){return false;}}
 public void order(String id){if(!visibleOrder(id))throw CurrentActor.missing();}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(this);}
 private static Long number(String value){try{return Long.valueOf(value);}catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid identity");}}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
  // Service token caller/scope is already enforced by RoutePolicy. Never infer a human actor.
  if(CurrentActor.technical())return true;
  String p=request.getRequestURI().substring(request.getContextPath().length()),method=request.getMethod();
  if(method.equals("GET")&&p.equals("/tickets")){
   var order=request.getParameter("orderId");var client=request.getParameter("clientId");
   if(order!=null&&client==null)order(order.trim());
   if(client!=null&&!CurrentActor.admin())CurrentActor.self(number(client.trim()));
  }
  return true;
 }
 @Override public boolean supports(MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){return true;}
 @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){

  return body;
 }
}
