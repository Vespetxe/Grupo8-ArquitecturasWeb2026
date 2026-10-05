package grupo22.integrador3.Repositories;

import grupo22.integrador3.DTO.ReporteCarreraDTO;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import grupo22.integrador3.Entitites.EstudianteCarreraPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("EstudianteCarreraRepository")
public interface EstudianteCarreraRepository extends JpaRepository<EstudianteCarrera, EstudianteCarreraPK> {

    void matricularEstudiante(Long DNI, Long idCarrera);

    public List<EstudianteCarrera> obtenerEstudianteCarreraPorEstudiante(Long dni);

    List<ReporteCarreraDTO> getReporteCarreras();
}
