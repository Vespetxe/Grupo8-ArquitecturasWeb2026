package grupo22.integrador3.Repositories;

import grupo22.integrador3.Entitites.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EstudianteRepository extends JpaRepository<Estudiante, Integer> {

    void insertarDesdeCSV(String rutaArchivo);

    void guardarEstudiante(Estudiante estudiante);

    List<Estudiante> obtenerEstudiantesOrdenados();

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

    Estudiante obtenerEstudiantePorDNI (Integer dni);

    Estudiante obtenerEstudiantePorLU (Integer libreta_estudiantil);

    List<Estudiante> obtenerEstudiantesPorGenero(String genero);
}

