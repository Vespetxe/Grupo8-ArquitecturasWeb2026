package ar.edu.unicen.grupo8.comprasservice.client;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import ar.edu.unicen.grupo8.comprasservice.security.ServiceTokens;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
@Component @Profile("integracion")
public class RestEventoClient implements EventoClient {
    record Precio(Long sectorId,BigDecimal price){}
    record Evento(Long eventId,String status,OffsetDateTime startDateTime,List<Precio> sectorPrices){}
    private final RestClient rest;
    private final ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard guard;
    @org.springframework.beans.factory.annotation.Autowired
    public RestEventoClient(@Value("${ticketya.eventos.base-url:http://localhost:8081}") String url,ServiceTokens tokens,org.springframework.core.env.Environment env){rest=tokens.client("eventos",url);guard=new ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard(env);}
    public RestEventoClient(String url,ServiceTokens tokens){this(url,tokens,new org.springframework.core.env.StandardEnvironment());}
    private Evento get(Long id){return RestSupport.read(RestSupport.Operation.EVENT_READ,()->guard.execute(()->readValidated(id)));}
    private Evento readValidated(Long id){var e=rest.get().uri("/api/v1/events/{id}",id).retrieve().body(Evento.class);
        RestSupport.require(e!=null&&id.equals(e.eventId())&&e.startDateTime()!=null&&e.sectorPrices()!=null
            && java.util.Set.of("BORRADOR","PUBLICADO","CANCELADO","SUSPENDIDO","AGOTADO","FINALIZADO").contains(e.status()==null?"":e.status())
            && e.sectorPrices().stream().allMatch(p->p!=null&&p.sectorId()!=null&&p.sectorId()>0&&p.price()!=null&&p.price().signum()>0)
            && e.sectorPrices().stream().map(Precio::sectorId).distinct().count()==e.sectorPrices().size());return e;}
    public boolean eventoValido(Long id){var e=get(id);return "PUBLICADO".equals(e.status()) && e.startDateTime().isAfter(OffsetDateTime.now(ZoneOffset.UTC));}
    public LocalDateTime obtenerFechaInicio(Long id){return get(id).startDateTime().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();}
    public BigDecimal obtenerPrecioSector(Long id,Long sector){var e=get(id);if(!"PUBLICADO".equals(e.status()))throw new IllegalArgumentException("Evento no publicado");return e.sectorPrices().stream().filter(p->sector.equals(p.sectorId())).findFirst().orElseThrow(()->new IllegalArgumentException("Sector inexistente")).price();}
    public boolean eventoCancelado(Long id){return "CANCELADO".equals(get(id).status());}
    public void registrarCancelacion(Long id){if(!eventoCancelado(id))throw new IllegalArgumentException("Eventos no confirma la cancelacion");}
}
