package example.microservicio_inventario_reservas.queue;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @RequestMapping("/colas/eventos/{evento}")
@Tag(name="Fila virtual",description="FIFO por evento de alta demanda. JWT CLIENTE para su propio turno; ADMINISTRADOR configura antes de la primera reserva. Posicion 0 indica habilitacion; fechas UTC. 401 sin JWT, 403 rol incorrecto, 404 evento inexistente, 409 turno/configuracion no compatible.")
public class ColaController {
 private final ColaService cola;
 public ColaController(ColaService cola){this.cola=cola;}
 public record Configuracion(int tamanoTanda,int turnoSegundos){}
 @PutMapping @Operation(summary="Marcar alta demanda; configuracion idempotente e inmutable")
 public ColaService.Configuracion configurar(@PathVariable Long evento,@RequestBody Configuracion config){return cola.configurar(evento,config.tamanoTanda(),config.turnoSegundos());}
 @PostMapping @Operation(summary="Ingresar con identidad JWT; repetir no duplica un turno vigente")
 public ResponseEntity<ColaService.EstadoTurno> ingresar(@PathVariable Long evento){return response(cola.ingresar(evento));}
 @GetMapping("/mi-turno") @Operation(summary="Consultar turno propio y avanzar expiraciones/tandas de forma atomica")
 public ResponseEntity<ColaService.EstadoTurno> consultar(@PathVariable Long evento){return response(cola.consultar(evento));}
 @DeleteMapping("/mi-turno") @Operation(summary="Abandonar turno propio; nunca libera una reserva existente")
 public ResponseEntity<ColaService.EstadoTurno> abandonar(@PathVariable Long evento){return response(cola.abandonar(evento));}
 private ResponseEntity<ColaService.EstadoTurno> response(ColaService.EstadoTurno body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
