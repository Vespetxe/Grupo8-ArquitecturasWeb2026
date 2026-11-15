package ar.edu.tudai.ticketya.servicio_entradas.access;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
/** JWT PERSONAL_ACCESO plus event assignment; ADMINISTRADOR manages permissions. */
@RestController
public class AdmissionController {
 private final AdmissionService service;
 public AdmissionController(AdmissionService service){this.service=service;}
 public record Scan(String qr){}
 @PutMapping("/admin/accesos/eventos/{event}/personal/{staff}")
 public AdmissionService.Assignment assign(@PathVariable String event,@PathVariable Long staff){return service.assign(event,staff,true);}
 @DeleteMapping("/admin/accesos/eventos/{event}/personal/{staff}")
 public AdmissionService.Assignment revoke(@PathVariable String event,@PathVariable Long staff){return service.assign(event,staff,false);}
 @PostMapping("/accesos/eventos/{event}/validaciones")
 public ResponseEntity<AdmissionService.Result> validate(@PathVariable String event,@RequestBody Scan scan){var decision=service.validate(event,scan.qr());return ResponseEntity.status(decision.status()).cacheControl(CacheControl.noStore()).body(decision.result());}
}
