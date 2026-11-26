package grupo22.eventocatalogo_service.feign;
public final class FeignFixtures {
 private FeignFixtures(){}
 public interface Probe {@org.springframework.web.bind.annotation.PostMapping("/write") void write(); @org.springframework.web.bind.annotation.GetMapping("/slow") void slow();}
 public static void stub(grupo22.eventocatalogo_service.security.ServiceTokens tokens,org.springframework.core.env.Environment env){
  var transport=new grupo22.eventocatalogo_service.timing.TimedTransport(env);
  org.mockito.Mockito.doAnswer(call->FeignSupport.target(call.getArgument(0),FeignSupport.transport(transport.factory(false),transport.propagate()),call.getArgument(2))).when(tokens).feign(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
 }
}
