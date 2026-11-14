package grupo22.eventocatalogo_service.security;
@org.springframework.web.bind.annotation.RestControllerAdvice
@org.springframework.core.annotation.Order(-100)
public class TechnicalAuthErrors {
 @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
 public org.springframework.http.ResponseEntity<?> forbidden(){return org.springframework.http.ResponseEntity.status(403).body(java.util.Map.of("status",403,"message","Forbidden"));}
 @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 public org.springframework.http.ResponseEntity<?> status(org.springframework.web.server.ResponseStatusException e){return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("status",e.getStatusCode().value(),"message",e.getReason()==null?"Request failed":e.getReason()));}

 @org.springframework.web.bind.annotation.ExceptionHandler(TechnicalAuthFailure.class)
 public org.springframework.http.ResponseEntity<?> unavailable(){return org.springframework.http.ResponseEntity.status(503).body(java.util.Map.of("status",503,"message","Technical authentication unavailable"));}
}
