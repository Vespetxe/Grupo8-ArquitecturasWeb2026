package ar.edu.tudai.ticketya.servicio_entradas.observability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
/** Request-local state. Contains no token, exception, body, query or credentials. */
public final class HttpLogState {
 public static final String ATTRIBUTE=HttpLogState.class.getName();
 private static final Logger LOG=LoggerFactory.getLogger(HttpLogState.class);
 public enum Completion {COMPLETED,ERROR,CANCELLED}
 public enum Category {NONE,BAD_REQUEST,AUTHENTICATION,AUTHORIZATION,NOT_FOUND,CONFLICT,CLIENT_ERROR,INTERNAL,UNAVAILABLE,TIMEOUT,CANCELLED}
 private volatile Audit.Actor auditActor=Audit.actor(null);
 private record Actor(String type,String id){}
 private volatile String correlationId;
 private final String method,route;
 private volatile String traceId,spanId;
 private final long started=System.nanoTime();
 private final AtomicReference<Actor> actor=new AtomicReference<>(new Actor("ANONYMOUS",null));
 private final AtomicReference<Category> failure=new AtomicReference<>(Category.NONE);
 private final AtomicBoolean finished=new AtomicBoolean();
 private final AtomicBoolean failed=new AtomicBoolean();
 public HttpLogState(String id,String method,String path){if(!CorrelationIds.valid(id))throw new IllegalArgumentException("Invalid correlation format");this.correlationId=id;this.method=HttpRoutes.method(method);this.route=HttpRoutes.route(path);}
 public String correlationId(){return correlationId;}
 public String traceId(){return traceId;}
 public String spanId(){return spanId;}
 public void authenticatedCorrelation(java.util.List<String> ids){if(ids.size()==1&&CorrelationIds.valid(ids.get(0)))correlationId=ids.get(0);}
 public void trace(io.micrometer.tracing.Tracer tracer){var span=tracer==null?null:tracer.currentSpan();if(span!=null){traceId=span.context().traceId();spanId=span.context().spanId();}}

 public void authenticated(Authentication auth){
  auditActor=Audit.actor(auth);
  if(auth instanceof JwtAuthenticationToken token&&auth.isAuthenticated()){
   String use=token.getToken().getClaimAsString("token_use"),subject=token.getName();
   if("user".equals(use)&&subject.matches("[1-9][0-9]{0,18}"))actor.set(new Actor("USER",subject));
   else if("service".equals(use)&&subject.matches("service:(eventos|compras|entradas)-to-(eventos|inventario|entradas|compras)"))actor.set(new Actor("SERVICE",subject));
  }
 }
 public void timeout(){failed.set(true);failure.set(Category.TIMEOUT);}
 public void failure(Throwable error){failed.set(true);transportFailure(error);}
 public void transportFailure(Throwable error){Category kind=transportCategory(error);if(kind!=Category.NONE){failed.set(true);failure.set(kind);}}
 private static Category transportCategory(Throwable error){
  var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
  Throwable current=error;
  for(int i=0;i<8&&current!=null&&seen.add(current);i++,current=current.getCause()){
   String type=current.getClass().getName();
   if(current instanceof java.util.concurrent.TimeoutException||current instanceof java.net.SocketTimeoutException||type.equals("io.netty.handler.timeout.ReadTimeoutException")||type.equals("org.springframework.web.context.request.async.AsyncRequestTimeoutException")){return Category.TIMEOUT;}
   if(current instanceof java.util.concurrent.CancellationException||type.equals("org.apache.catalina.connector.ClientAbortException")||type.equals("org.springframework.web.context.request.async.AsyncRequestNotUsableException")||type.equals("reactor.netty.channel.AbortedException")){return Category.CANCELLED;}
  }
  return Category.NONE;
 }
 public boolean cancelled(){return failure.get()==Category.CANCELLED;}
 public void finish(int status,Completion completion){
  if(!finished.compareAndSet(false,true))return;
  Category category=failure.get();
  if(completion==Completion.CANCELLED)category=Category.CANCELLED;
  else if(category==Category.NONE)category=switch(status){case 400->Category.BAD_REQUEST;case 401->Category.AUTHENTICATION;case 403->Category.AUTHORIZATION;case 404->Category.NOT_FOUND;case 409->Category.CONFLICT;case 503->Category.UNAVAILABLE;case 504->Category.TIMEOUT;default->status>=500?Category.INTERNAL:status>=400?Category.CLIENT_ERROR:Category.NONE;};
  if(category==Category.NONE&&(completion==Completion.ERROR||failed.get()))category=Category.INTERNAL;
  if(completion==Completion.COMPLETED&&failed.get())completion=Completion.ERROR;
  Actor who=actor.get();
  var event=status>=500?LOG.atError():category!=Category.NONE?LOG.atWarn():LOG.atInfo();
  event.addKeyValue("eventType","HTTP_REQUEST").addKeyValue("correlationId",correlationId)
   .addKeyValue("httpMethod",method).addKeyValue("httpRoute",route).addKeyValue("httpStatus",status)
   .addKeyValue("durationMs",Math.max(0L,System.nanoTime()-started)/1_000_000.0)
   .addKeyValue("actorType",who.type()).addKeyValue("errorCategory",category.name()).addKeyValue("completion",completion.name());
  if(who.id()!=null)event.addKeyValue("actorId",who.id());
  if(traceId!=null){event.addKeyValue("trace.id",traceId).addKeyValue("span.id",spanId);}
  event.log("HTTP request completed");
  if(status>=400)Audit.emit(Audit.http(auditActor,correlationId,traceId,spanId,status));
 }
}
