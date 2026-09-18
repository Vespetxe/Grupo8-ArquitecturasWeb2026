package repository;

import entities.Estudiante;

public interface EstudianteRepository {

    void insertarDesdeCSV(String rutaArchivo);

    void saveEstudiante(Estudiante estudiante);
}
