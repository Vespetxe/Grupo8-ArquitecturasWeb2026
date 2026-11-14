package example.microservicio_inventario_reservas.security;
import jakarta.servlet.http.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.core.MethodParameter;
import java.lang.reflect.Type;
import example.microservicio_inventario_reservas.repositories.*;
import example.microservicio_inventario_reservas.dtos.CrearReservaRequest;
/** HTTP boundary checks run before controllers, including GETs with persistence side effects. */
@RestControllerAdvice
public class ResourceOwnership extends RequestBodyAdviceAdapter implements HandlerInterceptor, WebMvcConfigurer {
 private final ReservaRepository reservas;
 public ResourceOwnership(ReservaRepository reservas){this.reservas=reservas;}
 public void reservation(Long id){CurrentActor.own(reservas.findById(id).orElseThrow(CurrentActor::missing).getIdentityOwnerId());}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(this);}
 private static Long number(String value){try{Long id=Long.valueOf(value);if(id<=0)throw new IllegalArgumentException();return id;}catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid identity");}}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
  // Service token caller/scope is already enforced by RoutePolicy. Never infer a human actor.
  if(CurrentActor.technical())return true;
  String p=request.getRequestURI().substring(request.getContextPath().length()),method=request.getMethod();
  if(method.equals("GET")&&p.equals("/reservas")){if(!CurrentActor.admin())CurrentActor.self(number(request.getParameter("idCliente")));}
  if(method.equals("GET")&&p.matches("/reservas/[0-9]+"))reservation(Long.valueOf(p.split("/")[2]));
  return true;
 }
 @Override public boolean supports(MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){return true;}
 @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){
  if(body instanceof CrearReservaRequest dto)CurrentActor.self(dto.getIdCliente());
  return body;
 }
}
