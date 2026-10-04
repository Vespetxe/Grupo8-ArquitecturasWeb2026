package grupo22.integrador3.Repositories;

import grupo22.integrador3.DTO.ReporteCarreraDTO;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstudianteCarreraRepository extends JpaRepository<EstudianteCarrera, Long> {

    void matricularEstudiante(Long DNI, Long idCarrera);

    public List<EstudianteCarrera> obtenerEstudianteCarreraPorEstudiante(Long dni);

    List<ReporteCarreraDTO> getReporteCarreras();
}
