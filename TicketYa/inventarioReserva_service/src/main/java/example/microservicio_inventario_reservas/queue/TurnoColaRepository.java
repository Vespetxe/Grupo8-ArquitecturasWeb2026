package example.microservicio_inventario_reservas.queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface TurnoColaRepository extends JpaRepository<TurnoCola,Long> {
 Optional<TurnoCola> findByEventoAndCliente(Long evento,Long cliente);
 List<TurnoCola> findByEventoAndEstadoOrderByNumeroAsc(Long evento,TurnoCola.Estado estado,Pageable page);
 long countByEventoAndEstadoAndNumeroLessThan(Long evento,TurnoCola.Estado estado,long numero);
 long countByEventoAndEstado(Long evento,TurnoCola.Estado estado);
}
