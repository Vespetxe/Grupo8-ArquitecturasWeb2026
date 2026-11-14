package ar.edu.ticketya.identidad.observability;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AuditCapture implements AutoCloseable {
    public final List<ILoggingEvent> events=new CopyOnWriteArrayList<>();
    private final Logger logger=(Logger)LoggerFactory.getLogger(Audit.class);
    private final boolean additive=logger.isAdditive();
    private final AppenderBase<ILoggingEvent> appender=new AppenderBase<>(){
        @Override protected void append(ILoggingEvent event){event.prepareForDeferredProcessing();events.add(event);}
    };
    public AuditCapture(){appender.setContext(logger.getLoggerContext());appender.start();logger.setAdditive(false);logger.addAppender(appender);}
    public Map<String,Object> fields(ILoggingEvent event){var result=new HashMap<String,Object>();if(event.getKeyValuePairs()!=null)event.getKeyValuePairs().forEach(k->result.put(k.key,k.value));return result;}
    public List<Map<String,Object>> rows(){return events.stream().map(this::fields).filter(m->"AUDIT".equals(m.get("eventType"))).toList();}
    public Map<String,Object> find(String action)throws Exception{
        long deadline=System.nanoTime()+4_000_000_000L;
        while(System.nanoTime()<deadline){var row=rows().stream().filter(m->action.equals(m.get("action"))).findFirst();if(row.isPresent())return row.get();Thread.sleep(10);}
        throw new AssertionError("Expected audit action not found");
    }
    @Override public void close(){logger.detachAppender(appender);appender.stop();logger.setAdditive(additive);}
}
