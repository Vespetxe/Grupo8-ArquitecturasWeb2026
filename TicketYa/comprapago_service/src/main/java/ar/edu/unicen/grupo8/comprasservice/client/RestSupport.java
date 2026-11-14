package ar.edu.unicen.grupo8.comprasservice.client;
import org.springframework.web.client.*;
import ar.edu.unicen.grupo8.comprasservice.exception.OperacionExternaRechazadaException;
public final class RestSupport {
    public enum Operation { UNSPECIFIED, EVENT_READ, RESERVATION_READ, RESERVATION_CONFIRM, RESERVATION_RELEASE,
        RESERVATION_COMPENSATE, RESERVATION_RETURN, RETURN_READ, TICKETS_READ, TICKETS_ISSUE, TICKETS_ANNUL, TICKET_STATUS }
    private RestSupport(){}
    public static void mutate(Runnable action){mutate(Operation.UNSPECIFIED,action);}
    public static void mutate(Operation operation,Runnable action){read(operation,()->{action.run();return null;});}
    public static <T> T read(Operation operation,java.util.function.Supplier<T> action){
        try {return action.get();}
        catch(RemoteFailure failure){throw failure;}
        catch(RestClientResponseException ex){
            if(ex.getStatusCode().value()==401||ex.getStatusCode().value()==403)
                throw new ar.edu.unicen.grupo8.comprasservice.security.TechnicalAuthFailure();
            // Only this endpoint has an existing machine-readable, definitive
            // validation contract. Generic 409/404 (including hidden ownership)
            // and empty/malformed error bodies prove no commercial rejection.
            if(operation==Operation.TICKETS_ISSUE && ex.getStatusCode().value()==422 && errorCode(ex).equals("INVALID_ORDER"))
                throw new OperacionExternaRechazadaException("Emission rejected by validated order contract");
            int status=ex.getStatusCode().value();
            throw new RemoteFailure(status==429?RemoteFailure.Category.RATE_LIMITED:status>=500?RemoteFailure.Category.UNAVAILABLE:RemoteFailure.Category.UNCONFIRMED_REJECTION);
        }
        catch(ar.edu.unicen.grupo8.comprasservice.security.TechnicalAuthFailure failure){throw failure;}
        catch(RestClientException failure){throw new RemoteFailure(RemoteFailure.Category.RESULT_UNKNOWN);}
    }
    private static String errorCode(RestClientResponseException ex){
        if(ex.getResponseBodyAsByteArray().length>4096)return "";
        try {var node=new tools.jackson.databind.ObjectMapper().readTree(ex.getResponseBodyAsString());return node.isObject()&&node.path("code").isTextual()&&node.path("message").isTextual()?node.path("code").asText(""):"";}
        catch(RuntimeException malformed){return "";}
    }
    public static void require(boolean valid){if(!valid)throw new RemoteFailure(RemoteFailure.Category.INVALID_RESPONSE);}
}
