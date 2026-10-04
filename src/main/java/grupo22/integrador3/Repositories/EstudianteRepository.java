package grupo22.integrador3.Repositories;

import grupo22.integrador3.Entitites.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("EstudianteRepository")
public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    @Query("SELECT e FROM Estudiante e WHERE e.libreta_estudiantil = :libreta_estudiantil")
    public Optional<Estudiante> obtenerEstudiantePorLU(Integer libreta_estudiantil);

    List<Estudiante> obtenerEstudiantesOrdenados();

    public List<Estudiante> obtenerEstudiantesByCarreraAndCiudad(Long idCarrera, String ciudad);

    List<Estudiante> obtenerEstudiantesPorGenero(String genero);
}

