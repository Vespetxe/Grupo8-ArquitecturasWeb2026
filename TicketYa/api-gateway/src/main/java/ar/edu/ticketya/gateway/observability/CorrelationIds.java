package ar.edu.ticketya.gateway.observability;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
public final class CorrelationIds {
 public static final String HEADER="X-Correlation-Id";
 private static final Pattern UUID_V4=Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
 private CorrelationIds(){}
 public static String fresh(){return UUID.randomUUID().toString();}
 public static boolean valid(String value){return value!=null&&value.length()==36&&UUID_V4.matcher(value).matches();}
 public static String internal(List<String> values){return values!=null&&values.size()==1&&valid(values.get(0))?values.get(0):fresh();}
}
