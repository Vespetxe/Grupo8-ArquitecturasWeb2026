package grupo22.integrador3.Repositories;

import grupo22.integrador3.DTO.CarreraInscriptosDTO;
import grupo22.integrador3.DTO.ReporteCarreraDTO;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EstudianteCarreraRepository extends JpaRepository<EstudianteCarrera, Long> {

    void matricularEstudiante(Long DNI, Long idCarrera);

    public List<EstudianteCarrera> obtenerEstudianteCarreraPorEstudiante(Long dni);

    List<ReporteCarreraDTO> getReporteCarreras();

    @Query("SELECT new grupo22.integrador3.DTO.CarreraInscriptosDTO(c.id_carrera, c.nombre_carrera, COUNT(ec)) " +
            "FROM EstudianteCarrera ec " +
            "JOIN ec.carrera c " +
            "GROUP BY c.id_carrera, c.nombre_carrera " +
            "ORDER BY COUNT(ec) DESC")
    List<CarreraInscriptosDTO> obtenerInscriptosPorCarrera();
}
