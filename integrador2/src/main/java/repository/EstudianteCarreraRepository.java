package repository;

import dto.ReporteCarreraDTO;
import entities.EstudianteCarrera;

import java.util.List;

public interface EstudianteCarreraRepository {
    void insertarDesdeCSV(String rutaArchivo);

    void matricularEstudiante(Integer DNI, Integer idCarrera);

    public List<EstudianteCarrera> obtenerEstudianteCarreraPorEstudiante(Integer dni);

    List<ReporteCarreraDTO> getReporteCarreras();
}
