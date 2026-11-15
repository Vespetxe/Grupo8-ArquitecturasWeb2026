package ar.edu.tudai.ticketya.servicio_entradas.access;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface EventAccessRepository extends JpaRepository<EventAccess,Long>{
 Optional<EventAccess> findByEventIdAndStaffId(String eventId,Long staffId);
}
