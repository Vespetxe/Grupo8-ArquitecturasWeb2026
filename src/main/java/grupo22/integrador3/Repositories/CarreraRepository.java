package grupo22.integrador3.Repositories;



import grupo22.integrador3.DTO.CarreraDTO;
import grupo22.integrador3.Entitites.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface CarreraRepository extends JpaRepository<Carrera, Long> {


    // Ejercicio 2 Inciso H
    @Query("""
        SELECT c
        FROM Carrera c
        ORDER BY c.nombre_carrera
        """)
    List<Carrera> obtenerCarrerasOrdenadas();
}
