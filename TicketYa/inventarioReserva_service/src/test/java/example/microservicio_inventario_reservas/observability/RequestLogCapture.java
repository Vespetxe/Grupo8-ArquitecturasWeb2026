package example.microservicio_inventario_reservas.observability;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
/** Thread-safe capture, with the real production console encoder for JSON assertions. */
public final class RequestLogCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {
 private final List<ILoggingEvent> records=new CopyOnWriteArrayList<>();
 private final Logger logger=(Logger)LoggerFactory.getLogger(HttpLogState.class);
 public RequestLogCapture(){setContext(logger.getLoggerContext());start();logger.addAppender(this);}
 @Override protected void append(ILoggingEvent event){event.prepareForDeferredProcessing();records.add(event);}
 public Map<String,Object> fields(ILoggingEvent event){var result=new LinkedHashMap<String,Object>();if(event.getKeyValuePairs()!=null)event.getKeyValuePairs().forEach(p->result.put(p.key,p.value));return result;}
 public ILoggingEvent find(String id)throws InterruptedException {
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
  while(System.nanoTime()<deadline){for(var e:records)if(id.equals(fields(e).get("correlationId")))return e;Thread.sleep(5);}
  throw new AssertionError("No completion record for correlation ID");
 }
 public long count(String id){return records.stream().filter(e->id.equals(fields(e).get("correlationId"))).count();}
 public JsonNode json(ILoggingEvent event){
  var context=(LoggerContext)LoggerFactory.getILoggerFactory();
  @SuppressWarnings("unchecked") var console=(ConsoleAppender<ILoggingEvent>)context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
  return new ObjectMapper().readTree(new String(console.getEncoder().encode(event),StandardCharsets.UTF_8));
 }
 @Override public void close(){logger.detachAppender(this);stop();}
}
