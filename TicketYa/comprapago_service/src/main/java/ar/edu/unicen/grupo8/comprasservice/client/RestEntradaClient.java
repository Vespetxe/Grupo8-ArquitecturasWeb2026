package ar.edu.unicen.grupo8.comprasservice.client;
import ar.edu.unicen.grupo8.comprasservice.dto.*;
import ar.edu.unicen.grupo8.comprasservice.service.EntradaIdentidadService;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import ar.edu.unicen.grupo8.comprasservice.security.ServiceTokens;
import ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard;
import java.util.*;
@Component @Profile("integracion")
public class RestEntradaClient implements EntradaClient {
    private final RestClient rest;private final EntradaIdentidadService identidad;
    private final PilotReadGuard reads,issue;
    public record Ticket(String ticketId,String orderId,String eventId,String clientId,String sectorId,String seatId,String status,String qr){}
    @org.springframework.beans.factory.annotation.Autowired
    public RestEntradaClient(@Value("${ticketya.entradas.base-url:http://localhost:8083}") String url,EntradaIdentidadService identidad,ServiceTokens tokens,org.springframework.core.env.Environment env){
        rest=tokens.client("entradas",url);this.identidad=identidad;
        reads=new PilotReadGuard(env,PilotReadGuard.Family.TICKETS_READ);
        issue=new PilotReadGuard(env,PilotReadGuard.Family.TICKETS_ISSUE);
    }
    public RestEntradaClient(String url,EntradaIdentidadService identidad,ServiceTokens tokens){this(url,identidad,tokens,new org.springframework.core.env.StandardEnvironment());}
    public List<EntradaDTO> consultarEntradas(Long id){
        Ticket[] values=RestSupport.read(RestSupport.Operation.TICKETS_READ,()->reads.execute(()->readValidated(id)));
        return Arrays.stream(values).map(t->new EntradaDTO(registrar(t.ticketId()),Long.valueOf(t.orderId()),Long.valueOf(t.eventId()),Long.valueOf(t.clientId()),
                Long.valueOf(t.sectorId()),t.seatId()==null?null:Long.valueOf(t.seatId()),t.status(),t.qr())).toList();
    }
    private Ticket[] readValidated(Long id){
        Ticket[] values=rest.get().uri("/tickets?orderId={id}",id).retrieve().body(Ticket[].class);
        RestSupport.require(values!=null && Arrays.stream(values).allMatch(t->t!=null && validUuid(t.ticketId()) && id.toString().equals(t.orderId())
            && positive(t.eventId()) && positive(t.clientId()) && positive(t.sectorId()) && (t.seatId()==null||positive(t.seatId()))
            && Set.of("EMITIDA","ANULADA","REINTEGRADA").contains(t.status()==null?"":t.status()))
            && Arrays.stream(values).map(Ticket::ticketId).distinct().count()==values.length);
        return values;
    }
    private Long registrar(String uuid){try{return identidad.registrar(uuid);}catch(org.springframework.dao.DataIntegrityViolationException race){return identidad.registrar(uuid);}}
    static boolean validUuid(String value){try{return value!=null&&UUID.fromString(value).toString().equals(value);}catch(IllegalArgumentException invalid){return false;}}
    private static boolean positive(String value){try{return value!=null&&Long.parseLong(value)>0;}catch(NumberFormatException invalid){return false;}}
    public List<EntradaDTO> solicitarEntradas(Long compra,Long evento,Long cliente,List<DetalleEmisionDTO> detalles){
        RestSupport.mutate(RestSupport.Operation.TICKETS_ISSUE,()->issue.execute(()->rest.post().uri("/internal/tickets/issue").body(Map.of("orderId",compra.toString())).retrieve().toBodilessEntity()));
        return consultarEntradas(compra);
    }
    public void anularEntradas(Long compra){RestSupport.mutate(RestSupport.Operation.TICKETS_ANNUL,()->rest.post().uri("/internal/tickets/orders/{id}/annul",compra).retrieve().toBodilessEntity());}
    public void anularEntradas(Long compra,List<Long> ids){for(Long id:ids)RestSupport.mutate(RestSupport.Operation.TICKET_STATUS,()->rest.patch().uri("/internal/tickets/{id}/status",identidad.uuid(id))
            .body(Map.of("orderId",compra.toString(),"status","ANULADA")).retrieve().toBodilessEntity());}
}
