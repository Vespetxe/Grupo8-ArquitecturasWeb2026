package grupo22.eventocatalogo_service.security;
import jakarta.servlet.http.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.core.MethodParameter;
import java.lang.reflect.Type;
import grupo22.eventocatalogo_service.repository.*;
import grupo22.eventocatalogo_service.DTO.request.EventRequestDTO;
/** HTTP boundary checks run before controllers, including GETs with persistence side effects. */
@RestControllerAdvice
public class ResourceOwnership extends RequestBodyAdviceAdapter implements HandlerInterceptor, WebMvcConfigurer {
 private final EventRepository events;private final VenueRepository venues;
 public ResourceOwnership(EventRepository events,VenueRepository venues){this.events=events;this.venues=venues;}
 public void venue(Long id){CurrentActor.own(venues.findById(id).orElseThrow(CurrentActor::missing).getIdentityOwnerId());}
 public void event(Long id){CurrentActor.own(events.findById(id).orElseThrow(CurrentActor::missing).getIdentityOwnerId());}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(this);}
 private static Long number(String value){try{return Long.valueOf(value);}catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid identity");}}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
  // Service token caller/scope is already enforced by RoutePolicy. Never infer a human actor.
  if(CurrentActor.technical())return true;
  String p=request.getRequestURI().substring(request.getContextPath().length()),method=request.getMethod();
  if(method.equals("POST")&&p.matches("/api/v1/venues/[0-9]+/sectors"))venue(Long.valueOf(p.split("/")[4]));
  if(p.matches("/api/v1/events/[0-9]+(/(prices|publish))?")&&!method.equals("GET"))event(Long.valueOf(p.split("/")[4]));
  if(method.equals("GET")&&p.matches("/api/v1/events/[0-9]+")){
   var e=events.findById(Long.valueOf(p.split("/")[4])).orElseThrow(CurrentActor::missing);
   if(e.getStatus()==grupo22.eventocatalogo_service.domain.EventStatus.BORRADOR)CurrentActor.own(e.getIdentityOwnerId());
  }
  return true;
 }
 @Override public boolean supports(MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){return true;}
 @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){
  if(body instanceof EventRequestDTO dto){CurrentActor.self(dto.getOrganizerId());venue(dto.getVenueId());}
  return body;
 }
}
