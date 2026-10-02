package grupo22.integrador3.Repositories;

import grupo22.integrador3.Entitites.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstudianteRepository extends JpaRepository<Estudiante, Integer> {

    void insertarDesdeCSV(String rutaArchivo);

    void guardarEstudiante(Estudiante estudiante);

    List<Estudiante> obtenerEstudiantesOrdenados();

    public List<Estudiante> obtenerEstudiantesByCarreraAndCiudad(int idCarrera, String ciudad);

    Estudiante obtenerEstudiantePorDNI (Integer dni);

    Estudiante obtenerEstudiantePorLU (Integer libreta_estudiantil);

    List<Estudiante> obtenerEstudiantesPorGenero(String genero);
}

