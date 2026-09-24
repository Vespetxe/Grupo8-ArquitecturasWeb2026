package repository;

import entities.Estudiante;
import java.util.List;


public interface EstudianteRepository {

    void insertarDesdeCSV(String rutaArchivo);

    void guardarEstudiante(Estudiante estudiante);

    List<Estudiante> obtenerEstudiantesOrdenados();

    Estudiante obtenerEstudiantePorDNI (Integer dni);

    Estudiante obtenerEstudiantePorLU (Integer libreta_estudiantil);

    List<Estudiante> obtenerEstudiantesPorGenero(String genero);
}
