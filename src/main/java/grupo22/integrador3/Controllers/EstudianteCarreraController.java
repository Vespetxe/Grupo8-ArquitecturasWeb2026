package grupo22.integrador3.Controllers;

import grupo22.integrador3.DTO.ReporteCarreraDTO;
import grupo22.integrador3.Services.EstudianteCarreraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estudiante-carrera")
public class EstudianteCarreraController {

    @Autowired
    private EstudianteCarreraService estudianteCarreraService;

    // Ejercicio 2 Inciso H
    @GetMapping("/reporte")
    public List<ReporteCarreraDTO> getReporteCarreras() {
        return estudianteCarreraService.getReporteCarreras();
    }
}
