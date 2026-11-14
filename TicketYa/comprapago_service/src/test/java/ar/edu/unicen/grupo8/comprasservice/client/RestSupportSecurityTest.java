package ar.edu.unicen.grupo8.comprasservice.client;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import ar.edu.unicen.grupo8.comprasservice.security.TechnicalAuthFailure;
import ar.edu.unicen.grupo8.comprasservice.exception.OperacionExternaRechazadaException;
import static org.junit.jupiter.api.Assertions.*;
class RestSupportSecurityTest {
 @Test void authenticationIsTechnicalAndConflictRemainsCommercial(){
  for(var status:new HttpStatus[]{HttpStatus.UNAUTHORIZED,HttpStatus.FORBIDDEN})assertThrows(TechnicalAuthFailure.class,()->RestSupport.mutate(()->{throw new HttpClientErrorException(status);}));
  assertThrows(OperacionExternaRechazadaException.class,()->RestSupport.mutate(()->{throw new HttpClientErrorException(HttpStatus.CONFLICT);}));
 }
}
