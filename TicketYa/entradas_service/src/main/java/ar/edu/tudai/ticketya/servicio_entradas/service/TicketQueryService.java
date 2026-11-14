package ar.edu.tudai.ticketya.servicio_entradas.service;
import ar.edu.tudai.ticketya.servicio_entradas.api.TicketResponse;
import ar.edu.tudai.ticketya.servicio_entradas.repository.TicketRepository;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class TicketQueryService {
    private final TicketRepository tickets;
    @org.springframework.beans.factory.annotation.Autowired private ar.edu.tudai.ticketya.servicio_entradas.security.ResourceOwnership ownership;
    public TicketQueryService(TicketRepository tickets) { this.tickets = tickets; }
    public List<TicketResponse> byOrderId(String orderId) {
        return tickets.findAllByBatch_OrderIdOrderByTicketId(orderId).stream()
                .map(TicketResponse::from).toList();
    }
    public List<TicketResponse> byClientId(String clientId) {
        var visible = new java.util.HashMap<String,Boolean>();
        return tickets.findAllByClientIdOrderByTicketId(clientId).stream()
                .filter(t -> ar.edu.tudai.ticketya.servicio_entradas.security.CurrentActor.jwt()==null || ar.edu.tudai.ticketya.servicio_entradas.security.CurrentActor.technical() || ar.edu.tudai.ticketya.servicio_entradas.security.CurrentActor.admin() || visible.computeIfAbsent(t.getOrderId(), ownership::visibleOrder))
                .map(TicketResponse::from).toList();
    }
}
