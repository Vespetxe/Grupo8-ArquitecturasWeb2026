package repository;

import dto.CarreraDTO;
import entities.Carrera;
import java.util.List;


public interface CarreraRepository {
    void insertCarrera(int id_carrera, String nombre_carrera, int duracion_carrera);
    List<CarreraDTO> findCarrerasConMasIncriptos();
}
