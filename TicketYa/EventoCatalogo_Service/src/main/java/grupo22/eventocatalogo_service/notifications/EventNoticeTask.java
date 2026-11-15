package grupo22.eventocatalogo_service.notifications;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @EnableScheduling
@ConditionalOnProperty(name="ticketya.notifications.dispatch-enabled",havingValue="true",matchIfMissing=true)
public class EventNoticeTask {
 private final EventNoticeDispatcher dispatcher;private final io.micrometer.tracing.Tracer tracer;
 public EventNoticeTask(EventNoticeDispatcher dispatcher,io.micrometer.tracing.Tracer tracer){this.dispatcher=dispatcher;this.tracer=tracer;}
 @Scheduled(fixedDelayString="${ticketya.notifications.dispatch-ms:5000}",initialDelayString="${ticketya.notifications.dispatch-ms:5000}")
 public void run(){var span=tracer.spanBuilder().setNoParent().name("notifications.delivery").start();try(var scope=tracer.withSpan(span)){dispatcher.dispatch();org.slf4j.LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("eventType","SCHEDULED_TASK").addKeyValue("trace.id",span.context().traceId()).addKeyValue("span.id",span.context().spanId()).log("Notification outbox scan completed");}finally{span.end();}}
}
