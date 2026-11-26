package ar.edu.unicen.grupo8.comprasservice.feign;
public final class FeignFixtures {
 private FeignFixtures(){}
 public interface Probe {@org.springframework.web.bind.annotation.PostMapping("/write") void write(); @org.springframework.web.bind.annotation.GetMapping("/slow") void slow();}
 public static void stub(ar.edu.unicen.grupo8.comprasservice.security.ServiceTokens tokens,org.springframework.core.env.Environment env){
  var transport=new ar.edu.unicen.grupo8.comprasservice.timing.TimedTransport(env);
  org.mockito.Mockito.doAnswer(call->FeignSupport.target(call.getArgument(0),FeignSupport.transport(transport.factory(false),transport.propagate()),call.getArgument(2))).when(tokens).feign(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
 }
}
