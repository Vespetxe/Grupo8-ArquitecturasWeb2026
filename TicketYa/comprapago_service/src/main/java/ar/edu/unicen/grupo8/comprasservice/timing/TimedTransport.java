package ar.edu.unicen.grupo8.comprasservice.timing;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.*;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.util.Timeout;
/** One bounded pool per caller. No automatic retry, redirect, cookies or credential replay. */
public final class TimedTransport implements AutoCloseable {
 public final long connectMs,readMs,commandMs,tokenMs,acquireMs;
 public final int maxConnections,perRoute;
 private final CloseableHttpClient client;
 private final PoolingHttpClientConnectionManager pool;
 public TimedTransport(Environment env){
  connectMs=value(env,"connect-ms",1000,1,5000);readMs=value(env,"read-ms",2000,1,10000);commandMs=value(env,"command-ms",4000,1,10000);
  tokenMs=value(env,"token-ms",3000,1,10000);acquireMs=value(env,"acquire-ms",500,1,2000);
  maxConnections=(int)value(env,"pool-size",40,1,200);perRoute=(int)value(env,"per-route",10,1,maxConnections);
  pool=PoolingHttpClientConnectionManagerBuilder.create().setMaxConnTotal(maxConnections).setMaxConnPerRoute(perRoute)
    .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(connectMs)).setSocketTimeout(Timeout.ofMilliseconds(Math.max(readMs,commandMs))).build())
    .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofMilliseconds(connectMs)).build()).build();
  client=HttpClients.custom().setConnectionManager(pool).disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
 }
 private static long value(Environment env,String name,long fallback,long min,long max){
  long v=env.getProperty("ticketya.http."+name,Long.class,fallback);if(v<min||v>max)throw new IllegalStateException("Invalid HTTP time/pool limit");return v;
 }
 public HttpComponentsClientHttpRequestFactory factory(boolean token){
  var factory=new HttpComponentsClientHttpRequestFactory(client);
  factory.setHttpContextFactory((method,uri)->{
   long reserve=connectMs+acquireMs+500;
   long read=RequestBudget.cap(token?tokenMs:method==HttpMethod.GET?readMs:commandMs,reserve);
   var context=HttpClientContext.create();context.setRequestConfig(RequestConfig.custom()
     .setConnectionRequestTimeout(Timeout.ofMilliseconds(RequestBudget.cap(acquireMs,connectMs+500)))
     .setResponseTimeout(Timeout.ofMilliseconds(read)).build());return context;
  });
  return factory;
 }
 public ClientHttpRequestInterceptor propagate(){
  return (request,body,execution)->{
   final ClientHttpResponse response;
   try{long remaining=RequestBudget.remaining();
    if(remaining!=Long.MAX_VALUE)request.getHeaders().set(RequestBudget.HEADER,String.valueOf(RequestBudget.cap(remaining,connectMs+acquireMs+500)));
    response=execution.execute(request,body);
   }catch(java.io.IOException|RuntimeException failure){recordTimeout(failure);throw failure;}
   // Bound slow bodies as well as headers without scheduling work or cancelling DB transactions.
   return new ClientHttpResponse(){
    public HttpStatusCode getStatusCode()throws java.io.IOException{return response.getStatusCode();}
    public String getStatusText()throws java.io.IOException{return response.getStatusText();}
    public HttpHeaders getHeaders(){return response.getHeaders();}
    public java.io.InputStream getBody()throws java.io.IOException{return new java.io.FilterInputStream(response.getBody()){
     private void check()throws java.io.IOException{if(RequestBudget.remaining()==0)throw new java.net.SocketTimeoutException("Request budget exhausted");}
     public int read()throws java.io.IOException{check();return super.read();}
     public int read(byte[] b,int off,int len)throws java.io.IOException{check();return super.read(b,off,len);}
    };}
    public void close(){response.close();}
   };
  };
 }
 private void recordTimeout(Throwable failure){
  String category=failure instanceof RequestBudget.Exhausted?"BUDGET_EXHAUSTED":null;
  for(int i=0;category==null&&i<8&&failure!=null;i++,failure=failure.getCause())if(failure instanceof java.net.SocketTimeoutException||failure.getClass().getSimpleName().contains("Timeout"))category="TIMEOUT";
  if(category!=null){var log=org.slf4j.LoggerFactory.getLogger(TimedTransport.class).atWarn().addKeyValue("eventType","DEPENDENCY_TIMEOUT").addKeyValue("errorCategory",category);
   var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();if(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet){var state=(ar.edu.unicen.grupo8.comprasservice.observability.HttpLogState)servlet.getRequest().getAttribute(ar.edu.unicen.grupo8.comprasservice.observability.HttpLogState.ATTRIBUTE);if(state!=null)log.addKeyValue("correlationId",state.correlationId());}
   log.log("HTTP dependency time limit reached");
  }
 }
 public int leased(){return pool.getTotalStats().getLeased();}
 @Override public void close()throws java.io.IOException{client.close();}
}
