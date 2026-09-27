package repository;

import dto.ReporteCarreraDTO;

import java.util.List;

public interface EstudianteCarreraRepository {
    void insertarDesdeCSV(String rutaArchivo);

    void matricularEstudiante(String dni, int idCarrera);

    List<ReporteCarreraDTO> getReporteCarreras();
}
