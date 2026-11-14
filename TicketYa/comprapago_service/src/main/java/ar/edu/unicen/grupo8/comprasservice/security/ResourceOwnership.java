package ar.edu.unicen.grupo8.comprasservice.security;
import jakarta.servlet.http.*;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.core.MethodParameter;
import java.lang.reflect.Type;
import ar.edu.unicen.grupo8.comprasservice.repository.*;
import ar.edu.unicen.grupo8.comprasservice.dto.*;
import ar.edu.unicen.grupo8.comprasservice.client.ReservaClient;
/** HTTP boundary checks run before controllers, including GETs with persistence side effects. */
@RestControllerAdvice
public class ResourceOwnership extends RequestBodyAdviceAdapter implements HandlerInterceptor, WebMvcConfigurer {
 private final CompraRepository compras;private final PagoRepository pagos;private final DevolucionRepository devoluciones;private final ReservaClient reservas;
 public ResourceOwnership(CompraRepository compras,PagoRepository pagos,DevolucionRepository devoluciones,ReservaClient reservas){this.compras=compras;this.pagos=pagos;this.devoluciones=devoluciones;this.reservas=reservas;}
 public void purchase(Long id){if(id==null||id<=0)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid identity");CurrentActor.own(compras.findById(id).orElseThrow(CurrentActor::missing).getIdentityOwnerId());}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(this);}
 private static Long number(String value){try{Long id=Long.valueOf(value);if(id<=0)throw new IllegalArgumentException();return id;}catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid identity");}}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
  // Service token caller/scope is already enforced by RoutePolicy. Never infer a human actor.
  if(CurrentActor.technical())return true;
  String p=request.getRequestURI().substring(request.getContextPath().length()),method=request.getMethod();
  if(p.matches("/compras/[0-9]+(/(estado|detalle|entradas|cancelar))?"))purchase(Long.valueOf(p.split("/")[2]));
  if(method.equals("GET")&&p.equals("/compras")&&!CurrentActor.admin())CurrentActor.self(number(request.getParameter("clienteId")));
  if(method.equals("GET")&&p.equals("/pagos"))purchase(number(request.getParameter("compraId")));
  if(method.equals("GET")&&p.matches("/pagos/[0-9]+"))purchase(pagos.findById(Long.valueOf(p.split("/")[2])).orElseThrow(CurrentActor::missing).getCompra().getId());
  if(method.equals("GET")&&p.matches("/devoluciones/[0-9]+"))purchase(devoluciones.findById(Long.valueOf(p.split("/")[2])).orElseThrow(CurrentActor::missing).getCompra().getId());
  return true;
 }
 @Override public boolean supports(MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){return true;}
 @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){
  if(body instanceof CrearCompraDTO dto && dto.getClienteId()!=null && dto.getReservaId()!=null){CurrentActor.self(dto.getClienteId());var reserva=reservas.consultarReserva(dto.getReservaId());if(reserva==null)throw CurrentActor.missing();CurrentActor.own(reserva.identityOwnerId());}
  if(body instanceof ProcesarPagoDTO dto && dto.getCompraId()!=null)purchase(dto.getCompraId());
  if(body instanceof SolicitarDevolucionDTO dto && dto.clienteId()!=null && dto.compraId()!=null){CurrentActor.self(dto.clienteId());purchase(dto.compraId());}
  return body;
 }
}
