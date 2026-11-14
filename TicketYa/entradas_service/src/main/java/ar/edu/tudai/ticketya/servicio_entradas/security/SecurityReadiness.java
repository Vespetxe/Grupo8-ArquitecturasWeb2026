package ar.edu.tudai.ticketya.servicio_entradas.security;
/** Internal authentication transport probe; reads no business data and performs no calls. */
@org.springframework.web.bind.annotation.RestController
public class SecurityReadiness {
 @org.springframework.web.bind.annotation.GetMapping("/internal/security/readiness")
 public java.util.Map<String,String> ready(){return java.util.Map.of("service","entradas","status","UP");}
}
