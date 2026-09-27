package repository;

public interface EstudianteCarreraRepository {
    void insertarDesdeCSV(String rutaArchivo);

    void matricularEstudiante(String dni, int idCarrera);
}
