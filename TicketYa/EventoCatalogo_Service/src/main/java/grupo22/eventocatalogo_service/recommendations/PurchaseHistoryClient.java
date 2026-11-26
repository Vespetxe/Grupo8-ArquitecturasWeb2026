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
 private final grupo22.eventocatalogo_service.feign.EventosComprasFeignClient client;
 private final PilotReadGuard guard;
 @org.springframework.beans.factory.annotation.Autowired public PurchaseHistoryClient(@org.springframework.context.annotation.Lazy grupo22.eventocatalogo_service.feign.EventosComprasFeignClient client,Environment env){this.client=client;guard=new PilotReadGuard(env,PilotReadGuard.Family.HISTORY);}
 public record History(List<Long> eventIds){}
 public PurchaseHistoryClient(ServiceTokens tokens,Environment env,@Value("${ticketya.compras.base-url:http://localhost:8084}") String url){client=tokens.feign(grupo22.eventocatalogo_service.feign.EventosComprasFeignClient.class,"compras",url);guard=new PilotReadGuard(env,PilotReadGuard.Family.HISTORY);}
 public Set<Long> events(Long owner){return guard.execute(()->{
  var response=client.history(owner);
  if(response==null||response.eventIds()==null||response.eventIds().stream().anyMatch(id->id==null||id<=0))throw new RestClientException("Invalid purchase history response");
  return Set.copyOf(response.eventIds());
 });}
}
