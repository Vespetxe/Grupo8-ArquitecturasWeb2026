package grupo22.integrador3.Controllers;

import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Services.EstudianteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estudiantes")
public class EstudianteController {

    @Autowired
    private EstudianteService estudianteService;

    // Ejercicio 2 Inciso G
    @GetMapping("/carrera/{idCarrera}/ciudad/{ciudad}")
    public List<Estudiante> obtenerEstudiantesByCarreraAndCiudad(
            @PathVariable int idCarrera,
            @PathVariable String ciudad) throws Exception {

        return estudianteService.obtenerEstudiantesByCarreraAndCiudad(
                idCarrera,
                ciudad
        );
    }
}
