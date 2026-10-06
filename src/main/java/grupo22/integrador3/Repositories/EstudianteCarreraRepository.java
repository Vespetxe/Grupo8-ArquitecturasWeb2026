package grupo22.integrador3.Repositories;

import grupo22.integrador3.DTO.CarreraInscriptosDTO;
import grupo22.integrador3.DTO.CarreraInscriptosDTO;
import grupo22.integrador3.DTO.ReporteCarreraDTO;
import grupo22.integrador3.Entitites.Carrera;
import grupo22.integrador3.Entitites.Estudiante;
import grupo22.integrador3.Entitites.EstudianteCarrera;
import grupo22.integrador3.Entitites.EstudianteCarreraPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

@Repository("EstudianteCarreraRepository")
public interface EstudianteCarreraRepository extends JpaRepository<EstudianteCarrera, EstudianteCarreraPK> {

    // Ejercicio 2 Inciso H

    // Obtiene la fecha de inscripción más antigua de una carrera
    @Query("""
        SELECT MIN(ec.inscripcion)
        FROM EstudianteCarrera ec
        WHERE ec.carrera = :carrera
        """)
    LocalDate obtenerPrimeraInscripcion(
            @Param("carrera") Carrera carrera
    );


    // Obtiene los estudiantes inscriptos en una carrera durante un año
    @Query("""
        SELECT ec.estudiante
        FROM EstudianteCarrera ec
        WHERE ec.carrera = :carrera
        AND YEAR(ec.inscripcion) = :anio
        """)
    List<Estudiante> obtenerInscriptosPorCarreraYAnio(
            @Param("carrera") Carrera carrera,
            @Param("anio") int anio
    );


    // Obtiene los estudiantes graduados de una carrera durante un año
    @Query("""
        SELECT ec.estudiante
        FROM EstudianteCarrera ec
        WHERE ec.carrera = :carrera
        AND YEAR(ec.graduacion) = :anio
        """)
    List<Estudiante> obtenerGraduadosPorCarreraYAnio(
            @Param("carrera") Carrera carrera,
            @Param("anio") int anio
    );
}
