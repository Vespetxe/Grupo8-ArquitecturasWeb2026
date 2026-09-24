package repository;

import entities.Estudiante;

import java.util.List;

public interface EstudianteRepository {

    void insertarDesdeCSV(String rutaArchivo);

    void saveEstudiante(Estudiante estudiante);

    List<Estudiante> findEstudiantesByCarreraAndCiudad(int idCarrera, String ciudad);

    List<Estudiante> obtenerTodosOrdenados();
}
