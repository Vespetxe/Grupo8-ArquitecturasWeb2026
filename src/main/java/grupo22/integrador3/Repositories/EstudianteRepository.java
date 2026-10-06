package grupo22.integrador3.Repositories;

import grupo22.integrador3.Entitites.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("EstudianteRepository")
public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    @Query("SELECT e FROM Estudiante e WHERE e.genero = :genero")
    public List<Estudiante> obtenerEstudiantesPorGenero(String genero);

    @Query("SELECT e FROM Estudiante e WHERE e.libreta_estudiantil = :libreta_estudiantil")
    public Optional<Estudiante> obtenerEstudiantePorLU(Integer libreta_estudiantil);

    //Ejercicio 2 Inciso G
    @Query("""
        SELECT e
        FROM EstudianteCarrera ec
        JOIN ec.estudiante e
        JOIN ec.carrera c
        WHERE c.id_carrera = :idCarrera
        AND e.ciudad = :ciudad
        """)
    List<Estudiante> obtenerEstudiantesByCarreraAndCiudad(
            @Param("idCarrera") int idCarrera,
            @Param("ciudad") String ciudad
    );

}

