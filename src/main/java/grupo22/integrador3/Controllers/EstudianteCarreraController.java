package grupo22.integrador3.Controllers;

import grupo22.integrador3.DTO.CarreraInscriptosDTO;
import grupo22.integrador3.DTO.ReporteCarreraDTO;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import grupo22.integrador3.Entitites.EstudianteCarreraPK;
import grupo22.integrador3.Services.EstudianteCarreraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;


@RestController
@RequestMapping("/estudiante-carrera")
public class EstudianteCarreraController {

    @Autowired
    private EstudianteCarreraService estudianteCarreraService;

    @GetMapping("")
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.status(HttpStatus.OK).body(estudianteCarreraService.findAll());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\":\"Error. Por favor intente más tarde.\"}");
        }
    }

    @GetMapping("/{dni}/{id_carrera}")
    public ResponseEntity<?> getById(@PathVariable Long dni, @PathVariable Long id_carrera) {
        try {
            EstudianteCarreraPK id = new EstudianteCarreraPK(dni, id_carrera);
            return ResponseEntity.status(HttpStatus.OK).body(estudianteCarreraService.findById(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\":\"Error. Por favor intente más tarde.\"}");
        }
    }

    //Ejercicio 2 Inciso B
    @PostMapping("")
    public ResponseEntity<?> save(@RequestBody EstudianteCarrera estudianteCarrera) {
        try {
            return ResponseEntity.status(HttpStatus.OK).body(estudianteCarreraService.save(estudianteCarrera));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\":\"Error. No se pudo ingresar, revise los campos e intente nuevamente.\"}");
        }
    }

    @PutMapping("/{dni}/{id_carrera}")
    public ResponseEntity<?> update(@PathVariable Long dni, @PathVariable Long id_carrera, @RequestBody EstudianteCarrera estudianteCarrera) {
        try {
            EstudianteCarreraPK id = new EstudianteCarreraPK(dni, id_carrera);
            return ResponseEntity.status(HttpStatus.OK).body(estudianteCarreraService.update(id, estudianteCarrera));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\":\"Error. No se pudo editar, revise los campos e intente nuevamente.\"}");
        }
    }

    @DeleteMapping("/{dni}/{id_carrera}")
    public ResponseEntity<?> delete(@PathVariable Long dni, @PathVariable Long id_carrera) {
        try {
            EstudianteCarreraPK id = new EstudianteCarreraPK(dni, id_carrera);
            return ResponseEntity.status(HttpStatus.OK).body(estudianteCarreraService.delete(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\":\"Error. no se pudo eliminar intente nuevamente.\"}");
        }
    }

    // Ejercicio 2 Inciso H
    @GetMapping("/reporte")
    public ResponseEntity<?> getReporteCarreras() {
        try {
            return ResponseEntity.status(HttpStatus.OK).body(estudianteCarreraService.getReporteCarreras());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\":\"Error. Por favor intente más tarde.\"}");
        }
    }


}
