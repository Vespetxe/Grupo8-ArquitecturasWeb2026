package repository;

import dto.CarreraDTO;
import entities.Carrera;
import java.util.List;


public interface CarreraRepository {
    List<CarreraDTO> findCarrerasConMasIncriptos();
}
