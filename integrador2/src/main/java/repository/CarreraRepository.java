package repository;

import dto.CarreraDTO;
import entities.Carrera;

import java.util.List;


public interface CarreraRepository {
    List<CarreraDTO> obtenerCarrerasConMasIncriptos();
    void insertarDesdeCSV(String archivo);
    public Carrera obtenerCarreraPorId (Integer id_carrera);
}
