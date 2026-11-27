package ar.edu.unicen.grupo8.comprasservice.client;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import ar.edu.unicen.grupo8.comprasservice.security.TechnicalAuthFailure;
import ar.edu.unicen.grupo8.comprasservice.exception.OperacionExternaRechazadaException;
import static org.junit.jupiter.api.Assertions.*;
class RemoteCallSupportSecurityTest {
 @Test void authenticationIsTechnicalAndUnknownConflictRemainsPending(){
  for(var status:new HttpStatus[]{HttpStatus.UNAUTHORIZED,HttpStatus.FORBIDDEN})assertThrows(TechnicalAuthFailure.class,()->RemoteCallSupport.mutate(()->{throw new HttpClientErrorException(status);}));
  assertThrows(RemoteFailure.class,()->RemoteCallSupport.mutate(()->{throw new HttpClientErrorException(HttpStatus.CONFLICT);}));
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={400,404,409,422,429,500,502,503,504})
 void statusAloneCannotAuthorizeCompensation(int status){
  for(var op:RemoteCallSupport.Operation.values()){
   var e=assertThrows(RemoteFailure.class,()->RemoteCallSupport.mutate(op,()->{throw response(status,"{\"code\":\"UNKNOWN\",\"message\":\"sentinel-secret\"}");}));
   assertFalse(e.toString().contains("sentinel-secret"));assertNull(e.getCause());
   if(status==429)assertEquals(RemoteFailure.Category.RATE_LIMITED,e.category());
  }
 }
 @Test void definitiveValidationIsRestrictedToTheEmissionContract(){
  var failure=response(422,"{\"code\":\"INVALID_ORDER\",\"message\":\"sentinel-secret\"}");
  assertThrows(OperacionExternaRechazadaException.class,()->RemoteCallSupport.mutate(RemoteCallSupport.Operation.TICKETS_ISSUE,()->{throw failure;}));
  assertThrows(RemoteFailure.class,()->RemoteCallSupport.mutate(RemoteCallSupport.Operation.RESERVATION_CONFIRM,()->{throw failure;}));
  assertThrows(RemoteFailure.class,()->RemoteCallSupport.mutate(RemoteCallSupport.Operation.TICKETS_ISSUE,()->{throw response(409,"{\"code\":\"ORDER_NOT_CONFIRMED\"}");}));
 }
 @Test void emptyMalformedAndTransportResponsesStayUnknown(){
  for(String body:new String[]{"","null","[]","broken","{\"code\":null}","{\"code\":\"INVALID_ORDER\"}"})assertThrows(RemoteFailure.class,()->RemoteCallSupport.mutate(RemoteCallSupport.Operation.TICKETS_ISSUE,()->{throw response(422,body);}));
  for(var cause:new java.io.IOException[]{new java.net.SocketTimeoutException("sentinel-secret"),new java.net.ConnectException("sentinel-secret"),new java.io.EOFException("sentinel-secret")}){
   var e=assertThrows(RemoteFailure.class,()->RemoteCallSupport.mutate(()->{throw new org.springframework.web.client.ResourceAccessException("sentinel-secret",cause);}));
   assertEquals(RemoteFailure.Category.RESULT_UNKNOWN,e.category());assertNull(e.getCause());
  }
 }
 static org.springframework.web.client.RestClientResponseException response(int code,String body){return new org.springframework.web.client.RestClientResponseException("sentinel-secret",code,"sentinel-secret",new org.springframework.http.HttpHeaders(),body.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8);}
}
