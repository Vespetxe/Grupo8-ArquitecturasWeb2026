package grupo22.eventocatalogo_service.recommendations;
import grupo22.eventocatalogo_service.security.ServiceTokens;
import grupo22.eventocatalogo_service.resilience.PilotReadGuard;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.client.*;
import java.util.*;
@Component
public class PurchaseHistoryClient {
 private final RestClient client;
 private final PilotReadGuard guard;
 public record History(List<Long> eventIds){}
 public PurchaseHistoryClient(ServiceTokens tokens,Environment env,@Value("${ticketya.compras.base-url:http://localhost:8084}") String url){client=tokens.client("compras",url);guard=new PilotReadGuard(env,PilotReadGuard.Family.HISTORY);}
 public Set<Long> events(Long owner){return guard.execute(()->{
  var response=client.get().uri("/internal/recommendations/customers/{id}/history",owner).retrieve().body(History.class);
  if(response==null||response.eventIds()==null||response.eventIds().stream().anyMatch(id->id==null||id<=0))throw new RestClientException("Invalid purchase history response");
  return Set.copyOf(response.eventIds());
 });}
}
