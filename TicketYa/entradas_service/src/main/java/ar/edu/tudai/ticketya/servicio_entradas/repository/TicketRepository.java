package ar.edu.tudai.ticketya.servicio_entradas.repository;
import ar.edu.tudai.ticketya.servicio_entradas.domain.Ticket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface TicketRepository extends JpaRepository<Ticket, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Ticket t where t.qrPayload=:qr")
    Optional<Ticket> lockByQr(@org.springframework.data.repository.query.Param("qr") String qr);
    @EntityGraph(attributePaths = "batch")
    List<Ticket> findAllByBatch_OrderIdOrderByTicketId(String orderId);
    @EntityGraph(attributePaths = "batch")
    List<Ticket> findAllByClientIdOrderByTicketId(String clientId);
    @EntityGraph(attributePaths = "batch")
    Optional<Ticket> findByTicketIdAndBatch_OrderId(String ticketId, String orderId);
    @EntityGraph(attributePaths = "batch")
    List<Ticket> findAllByBatch_OrderIdAndEventIdOrderByTicketId(String orderId, String eventId);
}
