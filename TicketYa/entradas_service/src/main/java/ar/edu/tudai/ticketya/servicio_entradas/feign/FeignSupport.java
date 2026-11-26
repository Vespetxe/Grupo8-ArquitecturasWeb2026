package ar.edu.tudai.ticketya.servicio_entradas.feign;
import feign.*;
import java.io.*;import java.net.URI;import java.util.*;
import org.springframework.http.*;import org.springframework.http.client.*;
import org.springframework.web.client.*;
/** Feign transport retaining bounded Spring/HC5 execution, without RestClient business calls. */
public final class FeignSupport {
 private FeignSupport(){}
 private static final tools.jackson.databind.ObjectMapper JSON=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
 public static Client transport(ClientHttpRequestFactory factory,ClientHttpRequestInterceptor... interceptors){
  var intercepted=new InterceptingClientHttpRequestFactory(factory,List.of(interceptors));
  return (request,options)->{
   try{
    var outgoing=intercepted.createRequest(URI.create(request.url()),HttpMethod.valueOf(request.httpMethod().name()));
    request.headers().forEach((k,v)->outgoing.getHeaders().put(k,new ArrayList<>(v)));
    if(request.body()!=null)outgoing.getBody().write(request.body());
    var response=outgoing.execute();
    Map<String,Collection<String>> headers=new LinkedHashMap<>();response.getHeaders().forEach((k,v)->headers.put(k,v));
    return Response.builder().request(request).status(response.getStatusCode().value()).reason(response.getStatusText()).headers(headers).body(response.getBody(),null).build();
   }catch(IOException error){throw new ResourceAccessException("Remote HTTP transport failed",error);}
  };
 }
 public static feign.codec.Encoder encoder(){return (body,type,template)->{try{template.header("Content-Type","application/json");template.body(JSON.writeValueAsBytes(body),java.nio.charset.StandardCharsets.UTF_8);}catch(RuntimeException failure){throw new RestClientException("Invalid outgoing JSON");}};}
 public static feign.codec.Decoder decoder(){return new org.springframework.cloud.openfeign.support.ResponseEntityDecoder((response,type)->{
  if(response.body()==null)return null;
  try(var stream=response.body().asInputStream()){byte[] bytes=stream.readAllBytes();return bytes.length==0?null:JSON.readValue(bytes,JSON.constructType(type));}
  catch(IOException|RuntimeException failure){throw new RestClientException("Invalid remote JSON response");}
 });}
 public static feign.codec.ErrorDecoder errors(){return (method,response)->{
  byte[] body=new byte[0];try{if(response.body()!=null)try(var stream=response.body().asInputStream()){body=stream.readNBytes(4097);}}catch(IOException ignored){}
  // Preserve existing endpoint-specific classification. Never log remote text or credentials.
  return new RestClientResponseException("Remote HTTP error",response.status(),"Remote failure",null,body,java.nio.charset.StandardCharsets.UTF_8);
 };}
 public static <T>T observedTarget(Class<T> type,Client client,String url,io.micrometer.observation.ObservationRegistry registry){return Feign.builder().addCapability(new SafeFeignCapability()).addCapability(new feign.micrometer.MicrometerObservationCapability(registry)).contract(new org.springframework.cloud.openfeign.support.SpringMvcContract()).encoder(encoder()).decoder(decoder()).errorDecoder(errors()).retryer(Retryer.NEVER_RETRY).client(client).target(type,url);}
 /** Programmatic equivalent for isolated adapter fixtures; runtime uses injected @FeignClient beans. */
 public static <T>T target(Class<T> type,Client client,String url){return Feign.builder().addCapability(new SafeFeignCapability()).contract(new org.springframework.cloud.openfeign.support.SpringMvcContract()).encoder(encoder()).decoder(decoder()).errorDecoder(errors()).retryer(Retryer.NEVER_RETRY).client(client).target(type,url);}
}
