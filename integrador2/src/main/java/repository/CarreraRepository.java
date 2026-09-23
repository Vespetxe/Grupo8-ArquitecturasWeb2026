package repository;

import dto.CarreraDTO;

import java.util.List;


public interface CarreraRepository {
    List<CarreraDTO> findCarrerasConMasIncriptos();
    void populateTable(String archivo);
}
