package grupo22.eventocatalogo_service.feign;
/** Keep endpoint/domain failure categories visible through Feign's decode boundary. */
public final class SafeFeignCapability implements feign.Capability {
 public feign.InvocationHandlerFactory enrich(feign.InvocationHandlerFactory factory){return (target,dispatch)->{
  var handler=factory.create(target,dispatch);
  return (proxy,method,args)->{try{return handler.invoke(proxy,method,args);}catch(feign.FeignException failure){
   Throwable cause=failure;for(int i=0;i<8&&cause!=null;i++,cause=cause.getCause())if(cause instanceof org.springframework.web.client.RestClientException)throw cause;
   throw new org.springframework.web.client.RestClientException("Remote exchange unconfirmed");
  }};
 };}
}
