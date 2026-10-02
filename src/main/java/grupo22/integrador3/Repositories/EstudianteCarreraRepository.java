package grupo22.integrador3.Repositories;

import grupo22.integrador3.DTO.ReporteCarreraDTO;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstudianteCarreraRepository extends JpaRepository<EstudianteCarrera, Integer> {
    void insertarDesdeCSV(String rutaArchivo);

    void matricularEstudiante(Integer DNI, Integer idCarrera);

    public List<EstudianteCarrera> obtenerEstudianteCarreraPorEstudiante(Integer dni);

    List<ReporteCarreraDTO> getReporteCarreras();
}
