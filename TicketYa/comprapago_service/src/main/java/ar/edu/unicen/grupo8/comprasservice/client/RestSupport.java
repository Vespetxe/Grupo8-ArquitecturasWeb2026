package ar.edu.unicen.grupo8.comprasservice.client;
import org.springframework.web.client.*;
import ar.edu.unicen.grupo8.comprasservice.exception.OperacionExternaRechazadaException;
public final class RestSupport {
    private RestSupport(){}
    public static void mutate(Runnable action){
        try {action.run();}
        catch(RestClientResponseException ex){
            if(ex.getStatusCode().value()==401||ex.getStatusCode().value()==403)
                throw new ar.edu.unicen.grupo8.comprasservice.security.TechnicalAuthFailure();
            if(ex.getStatusCode().is4xxClientError())throw new OperacionExternaRechazadaException("Operacion rechazada: HTTP "+ex.getStatusCode().value());
            throw ex;
        }
    }
}
