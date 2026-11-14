package example.microservicio_inventario_reservas.timing;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
/** Monotonic, request-local metadata. Never an identity, idempotency key or DB timeout. */
public final class RequestBudget {
 public static final String HEADER="X-TicketYa-Remaining-Ms";
 private static final String ATTRIBUTE=RequestBudget.class.getName();
 private record Deadline(long end,java.util.function.LongSupplier clock){}
 public static final class Exhausted extends org.springframework.web.client.RestClientException {public Exhausted(){super("Request budget exhausted; reconcile original operation");}}
 private RequestBudget(){}
 public static void start(jakarta.servlet.http.HttpServletRequest request,long configured){
  if(request.getAttribute(ATTRIBUTE)!=null)return;
  long allowed=configured;var values=java.util.Collections.list(request.getHeaders(HEADER));
  if(values.size()==1&&values.get(0).matches("[1-9][0-9]{0,5}"))allowed=Math.min(allowed,Long.parseLong(values.get(0)));
  request.setAttribute(ATTRIBUTE,new Deadline(System.nanoTime()+allowed*1_000_000L,System::nanoTime));
 }
 public static long remaining(){
  var attributes=RequestContextHolder.getRequestAttributes();
  if(attributes instanceof ServletRequestAttributes servlet&&servlet.getRequest().getAttribute(ATTRIBUTE) instanceof Deadline d)
   return Math.max(0,(d.end()-d.clock().getAsLong())/1_000_000L);
  return Long.MAX_VALUE;
 }
 public static boolean canStart(){return remaining()>2000;}
 public static long cap(long requested,long reserve){long remaining=remaining();if(remaining<=reserve)throw new Exhausted();return Math.max(1,Math.min(requested,remaining-reserve));}
 static void testDeadline(jakarta.servlet.http.HttpServletRequest request,long end,java.util.function.LongSupplier clock){request.setAttribute(ATTRIBUTE,new Deadline(end,clock));}
}
