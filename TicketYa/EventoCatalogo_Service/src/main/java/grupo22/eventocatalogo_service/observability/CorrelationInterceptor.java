package grupo22.eventocatalogo_service.observability;
/** Metadata only. Does not copy bearer tokens, actor headers, baggage or idempotency keys. */
public final class CorrelationInterceptor implements org.springframework.http.client.ClientHttpRequestInterceptor {
 @Override public org.springframework.http.client.ClientHttpResponse intercept(org.springframework.http.HttpRequest request,byte[] body,org.springframework.http.client.ClientHttpRequestExecution execution)throws java.io.IOException {
  var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  if(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet){
   var state=(HttpLogState)servlet.getRequest().getAttribute(HttpLogState.ATTRIBUTE);
   if(state!=null)request.getHeaders().set(CorrelationIds.HEADER,state.correlationId());
  }
  return execution.execute(request,body);
 }
}
