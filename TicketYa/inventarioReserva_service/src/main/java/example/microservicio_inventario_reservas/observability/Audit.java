package example.microservicio_inventario_reservas.observability;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Immutable allowlisted snapshots. Never retain entities, JWTs, arguments or exceptions. */
public final class Audit {
    private Audit() {}
    public enum Action { ACCESS_ATTEMPT, EVENT_PUBLICATION_LOCAL, EVENT_PUBLICATION, EVENT_PUBLICATION_REPLAY,
        EVENT_CANCELLATION_AUTHORIZED, EVENT_CANCELLATION_LOCAL, EVENT_CANCELLATION, EVENT_CANCELLATION_RETRY,
        RESERVATION_CREATED, RESERVATION_CONFIRMED, RESERVATION_RELEASED, RESERVATION_EXPIRED, RESERVATION_RETURN,
        PURCHASE_CREATED, PURCHASE_STAGE, PAYMENT_STATE, PAYMENT_PROCESS, PAYMENT_RECOVERY, PAYMENT_REPLAY,
        RETURN_REQUESTED, RETURN_DECIDED, REFUND_STATE, RETURN_PROCESS, REFUND_REPLAY,
        TICKETS_ISSUED, TICKETS_ANNULLED, TICKETS_REPLAY, LOGIN, PRIVILEGED_PROVISION, TECHNICAL_CLIENT_PROVISION }
    public enum Resource { HTTP, EVENT, RESERVATION, PURCHASE, PAYMENT, RETURN, REFUND, TICKET, USER, TECHNICAL_CLIENT }
    public enum Outcome { SUCCESS, REJECTED, FAILED, PENDING }
    public enum Error { NONE, HTTP_REJECTED, HTTP_FAILED, TX_ROLLBACK, TX_OUTCOME_UNKNOWN, NO_LOCAL_TRANSACTION, AUTHENTICATION_REJECTED, PROVISION_REJECTED }
    public record Actor(String type,String id,boolean verified) {
        public Actor {if(!java.util.Set.of("CLIENTE","ORGANIZADOR","ADMINISTRADOR","PERSONAL_ACCESO","SERVICIO","SISTEMA").contains(type)||!verified){type="SISTEMA";id=null;verified=false;}else if("SISTEMA".equals(type)){id=null;}else{id=canonical(id);if(id==null){type="SISTEMA";verified=false;}}}
    }
    public record Event(Action action,Resource resource,String id,String operationId,Outcome outcome,
                        String state,Actor actor,String correlationId,String traceId,String spanId,Long initiatorId,Error error) {
        public Event result(Outcome outcome,Error error) {return new Event(action,resource,id,operationId,outcome,state,actor,correlationId,traceId,spanId,initiatorId,error);}
        public Event rollback(){return new Event(action,resource,null,null,Outcome.FAILED,null,actor,correlationId,traceId,spanId,initiatorId,Error.TX_ROLLBACK);}
        public Event uncertain(){return new Event(action,resource,null,null,Outcome.PENDING,null,actor,correlationId,traceId,spanId,initiatorId,Error.TX_OUTCOME_UNKNOWN);}
        public Event by(Actor who){return new Event(action,resource,id,operationId,outcome,state,who,correlationId,traceId,spanId,initiatorId,error);}
        public Event initiated(Long id){return new Event(action,resource,this.id,operationId,outcome,state,actor,correlationId,traceId,spanId,id,error);}
    }
    public static Actor system(){return new Actor("SISTEMA",null,true);}
    public static Actor technical(String id){return new Actor("SERVICIO",canonical(id),true);}
    public static Actor actor(Authentication authentication){
        if(authentication instanceof JwtAuthenticationToken token && authentication.isAuthenticated()){
            String use=token.getToken().getClaimAsString("token_use"),id=canonical(token.getName());
            if(id!=null && "service".equals(use) && id.startsWith("service:"))return technical(id);
            if(id!=null && id.matches("[1-9][0-9]*") && "user".equals(use)){
                for(String role:new String[]{"ADMINISTRADOR","ORGANIZADOR","PERSONAL_ACCESO","CLIENTE"})
                    if(authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role)))return new Actor(role,id,true);
            }
        }
        // The system reports the attempt; no human identity is attributed to an unauthenticated caller.
        return new Actor("SISTEMA",null,false);
    }
    private static String canonical(Object value){
        if(value==null)return null;
        String id=value instanceof Long||value instanceof Integer?String.valueOf(value):value instanceof String?(String)value:null;
        return id!=null && (id.matches("[1-9][0-9]{0,18}") || CorrelationIds.valid(id)
            || id.matches("(?:service:)?(?:eventos|compras|entradas)-to-(?:eventos|inventario|entradas|compras)"))?id:null;
    }
    private static String safeOperation(String value){if(value==null)return null;int p=value.indexOf(':');if(p<1)return null;String prefix=value.substring(0,p);String id=canonical(value.substring(p+1));return id!=null&&java.util.Arrays.stream(Resource.values()).anyMatch(r->r.name().equals(prefix))?prefix+":"+id:null;}
    public static String operation(Resource resource,Object persistedId){String id=canonical(persistedId);return id==null?null:resource.name()+":"+id;}
    public static Event capture(Action action,Resource resource,Object persistedId,String operationId,Outcome outcome,Enum<?> state){
        String correlation=null,trace=MDC.get("traceId"),span=MDC.get("spanId");
        var attributes=RequestContextHolder.getRequestAttributes();
        if(attributes instanceof ServletRequestAttributes servlet){
            var http=(HttpLogState)servlet.getRequest().getAttribute(HttpLogState.ATTRIBUTE);
            if(http!=null){correlation=http.correlationId();if(http.traceId()!=null){trace=http.traceId();span=http.spanId();}}
        }
        if(trace==null||!trace.matches("[0-9a-f]{32}"))trace=null;
        if(span==null||!span.matches("[0-9a-f]{16}"))span=null;
        return new Event(action,resource,canonical(persistedId),safeOperation(operationId),outcome,state==null?null:state.name(),
            actor(SecurityContextHolder.getContext().getAuthentication()),correlation,trace,span,null,Error.NONE);
    }
    public static Event http(Actor actor,String correlation,String trace,String span,int status){
        return new Event(Action.ACCESS_ATTEMPT,Resource.HTTP,null,null,status>=500?Outcome.FAILED:Outcome.REJECTED,
            null,actor,correlation,trace,span,null,status>=500?Error.HTTP_FAILED:Error.HTTP_REJECTED);
    }
    /** Logging failure must not change an already committed business result. */
    private static String scope(Event event){if(event.outcome()==Outcome.REJECTED&&(event.action()==Action.LOGIN||event.action()==Action.PRIVILEGED_PROVISION))return "ATTEMPT";return switch(event.action()){case ACCESS_ATTEMPT->"ATTEMPT";case EVENT_PUBLICATION,EVENT_PUBLICATION_REPLAY,EVENT_CANCELLATION,EVENT_CANCELLATION_RETRY,PAYMENT_PROCESS,PAYMENT_REPLAY,PAYMENT_RECOVERY,REFUND_REPLAY,TICKETS_REPLAY->"ORCHESTRATION";default->"LOCAL_TRANSACTION";};}
    public static void emit(Event event){
        try {
            var log=LoggerFactory.getLogger(Audit.class).atInfo()
                .addKeyValue("eventType","AUDIT").addKeyValue("eventId",UUID.randomUUID().toString())
                .addKeyValue("timestamp",Instant.now().toString()).addKeyValue("action",event.action().name())
                .addKeyValue("auditScope",scope(event)).addKeyValue("resourceType",event.resource().name()).addKeyValue("outcome",event.outcome().name())
                .addKeyValue("actorType",event.actor().type()).addKeyValue("actorVerified",event.actor().verified());
            if(event.actor().id()!=null)log.addKeyValue("actorId",event.actor().id());
            if(event.id()!=null)log.addKeyValue("resourceId",event.id());
            if(safeOperation(event.operationId())!=null)log.addKeyValue("operationId",safeOperation(event.operationId()));
            if(event.state()!=null)log.addKeyValue("resourceState",event.state());
            if(event.correlationId()!=null)log.addKeyValue("correlationId",event.correlationId());
            if(event.traceId()!=null)log.addKeyValue("trace.id",event.traceId());
            if(event.spanId()!=null)log.addKeyValue("span.id",event.spanId());
            if(event.initiatorId()!=null)log.addKeyValue("initiatorId",event.initiatorId()).addKeyValue("initiatorType","ADMINISTRADOR");
            if(event.error()!=Error.NONE)log.addKeyValue("errorCode",event.error().name());
            log.log("Audit event");
        } catch(RuntimeException ignored) {
            try{LoggerFactory.getLogger(Audit.class).error("AUDIT_DELIVERY_FAILED");}catch(RuntimeException unavailable){/* preserve business result */}
        }
    }
}
