package ar.edu.tudai.ticketya.servicio_entradas.integration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
@Component
public class RestComprasOrderClient implements OrderClient {
    private final RestClient restClient;
    private final ar.edu.tudai.ticketya.servicio_entradas.resilience.PilotReadGuard guard;
    @org.springframework.beans.factory.annotation.Autowired
    public RestComprasOrderClient(RestClient.Builder builder, ar.edu.tudai.ticketya.servicio_entradas.security.ServiceTokens tokens,
            @Value("${ticketya.compras.base-url:http://localhost:8082}") String baseUrl,org.springframework.core.env.Environment env) {
        this.restClient = tokens.client("compras",baseUrl);
        this.guard = new ar.edu.tudai.ticketya.servicio_entradas.resilience.PilotReadGuard(env);
    }
    public RestComprasOrderClient(RestClient.Builder builder,ar.edu.tudai.ticketya.servicio_entradas.security.ServiceTokens tokens,String baseUrl){this(builder,tokens,baseUrl,new org.springframework.core.env.StandardEnvironment());}
    public RestComprasOrderClient(RestClient.Builder builder,String baseUrl){this(builder,baseUrl,new org.springframework.core.env.StandardEnvironment());}
    public RestComprasOrderClient(RestClient.Builder builder,String baseUrl,org.springframework.core.env.Environment env){this.restClient=builder.baseUrl(baseUrl).build();this.guard=new ar.edu.tudai.ticketya.servicio_entradas.resilience.PilotReadGuard(env);}
    @Override
    public OrderSnapshot getOrder(String orderId) {
        var order=getOrderForOwnership(orderId);
        if (!"CONFIRMADA".equalsIgnoreCase(order.status())) throw new OrderNotConfirmedException(orderId);
        return order;
    }
    @Override
    public OrderSnapshot getOrderForOwnership(String orderId) {
        try {
            return guard.execute(() -> readValidated(orderId));
        } catch (ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if(exception.getStatusCode().value()==401||exception.getStatusCode().value()==403)
                throw new ar.edu.tudai.ticketya.servicio_entradas.security.TechnicalAuthFailure();
            if (exception.getStatusCode().value() == 404 && knownAbsence(exception,orderId)) throw new OrderNotFoundException(orderId);
            throw new OrdersUnavailableException("Orders service returned HTTP "
                    + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new OrdersUnavailableException("Orders service is unavailable");
        }
    }
    private OrderSnapshot readValidated(String orderId) {
        var order=restClient.get().uri("/internal/orders/{orderId}",orderId).retrieve().body(OrderSnapshot.class);
        if (order == null || !orderId.equals(order.orderId()) || order.clientId() == null || order.clientId().isBlank()
                || order.status()==null || !java.util.Set.of("PENDIENTE","PROCESANDO","COMPLETADA","CANCELADA","FALLIDA","DEVUELTA_PARCIAL","DEVUELTA_TOTAL","CONFIRMADA").contains(order.status())
                || order.items() == null || order.items().isEmpty()
                || order.items().stream().anyMatch(i->i==null||i.eventId()==null||i.eventId().isBlank()||i.sectorId()==null||i.sectorId().isBlank()||i.quantity()<1||(i.seatId()!=null&&!i.seatId().isBlank()&&i.quantity()!=1))) {
            throw new OrdersUnavailableException("Orders service returned an invalid order response");
        }
        return order;
    }
    private boolean knownAbsence(RestClientResponseException failure,String id){
        if(failure.getResponseBodyAsByteArray().length>4096)return false;
        try{var body=new tools.jackson.databind.ObjectMapper().readTree(failure.getResponseBodyAsString());
            return body.path("status").asInt(0)==404 && (body.path("message").asText("").equals("No existe una compra con ID: "+id)
                ||body.path("message").asText("").equals("Resource not found"));
        }catch(RuntimeException malformed){return false;}
    }
}
