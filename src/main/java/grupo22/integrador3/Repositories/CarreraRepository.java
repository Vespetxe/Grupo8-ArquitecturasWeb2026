package grupo22.integrador3.Repositories;



import grupo22.integrador3.DTO.CarreraDTO;
import grupo22.integrador3.Entitites.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("CarreraRepository")
public interface CarreraRepository extends JpaRepository<Carrera, Long> {
    List<CarreraDTO> obtenerCarrerasConMasIncriptos();

    void insertarDesdeCSV(String archivo);
}
