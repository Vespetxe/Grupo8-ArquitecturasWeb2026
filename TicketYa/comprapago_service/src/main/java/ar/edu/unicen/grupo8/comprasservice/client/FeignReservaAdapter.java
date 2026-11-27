package ar.edu.unicen.grupo8.comprasservice.client;
import ar.edu.unicen.grupo8.comprasservice.dto.*;
import ar.edu.unicen.grupo8.comprasservice.service.EntradaIdentidadService;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import ar.edu.unicen.grupo8.comprasservice.feign.ComprasInventarioFeignClient;
import ar.edu.unicen.grupo8.comprasservice.security.ServiceTokens;
import ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard;
import java.time.*;
import java.util.*;
@Component @Profile("integracion")
public class FeignReservaAdapter implements ReservaClient {
    @org.springframework.beans.factory.annotation.Autowired
    public FeignReservaAdapter(@org.springframework.context.annotation.Lazy ComprasInventarioFeignClient feignClient,EntradaIdentidadService identidad,org.springframework.core.env.Environment env){this.feignClient=feignClient;this.identidad=identidad;reads=new PilotReadGuard(env,PilotReadGuard.Family.RESERVATION_READ);confirmation=new PilotReadGuard(env,PilotReadGuard.Family.RESERVATION_CONFIRM);}
    public record Asiento(Long idAsiento,String fila,Integer numero){}
    public record Reserva(Long idReserva,Long idCliente,Long idEvento,Long idSector,int cantidad,LocalDateTime fechaExpiracion,String estado,List<Asiento> asientosReservados,Long identityOwnerId){}
    private final ComprasInventarioFeignClient feignClient;private final EntradaIdentidadService identidad;
    private final PilotReadGuard reads,confirmation;
    public FeignReservaAdapter(@Value("${ticketya.inventario.base-url:http://localhost:8082}") String url,EntradaIdentidadService identidad,ServiceTokens tokens,org.springframework.core.env.Environment env){
        feignClient=tokens.feign(ComprasInventarioFeignClient.class,"inventario",url);this.identidad=identidad;
        reads=new PilotReadGuard(env,PilotReadGuard.Family.RESERVATION_READ);
        confirmation=new PilotReadGuard(env,PilotReadGuard.Family.RESERVATION_CONFIRM);
    }
    public FeignReservaAdapter(String url,EntradaIdentidadService identidad,ServiceTokens tokens){this(url,identidad,tokens,new org.springframework.core.env.StandardEnvironment());}
    public ReservaDTO consultarReserva(Long id){
        return RemoteCallSupport.read(RemoteCallSupport.Operation.RESERVATION_READ,()->reads.execute(()->readValidated(id)));
    }
    private ReservaDTO readValidated(Long id){
        var r=feignClient.get(id);
        RemoteCallSupport.require(r!=null && id.equals(r.idReserva()) && r.idCliente()!=null && r.idCliente()>0 && r.idEvento()!=null && r.idEvento()>0
            && r.idSector()!=null && r.idSector()>0 && r.cantidad()>0 && r.fechaExpiracion()!=null
            && Set.of("PENDIENTE","CONFIRMADA","LIBERADA","EXPIRADA").contains(r.estado()==null?"":r.estado()));
        RemoteCallSupport.require(r.asientosReservados()==null || r.asientosReservados().isEmpty() ||
            (r.asientosReservados().size()==r.cantidad() && r.asientosReservados().stream().allMatch(a->a!=null&&a.idAsiento()!=null&&a.idAsiento()>0)
                && r.asientosReservados().stream().map(Asiento::idAsiento).distinct().count()==r.cantidad()));
        var detalles=r.asientosReservados()!=null && !r.asientosReservados().isEmpty()
                ?r.asientosReservados().stream().map(a->new DetalleEmisionDTO(r.idSector(),a.idAsiento(),1)).toList()
                :List.of(new DetalleEmisionDTO(r.idSector(),null,r.cantidad()));
        return new ReservaDTO(r.idReserva(),r.idCliente(),r.idEvento(),"PENDIENTE".equals(r.estado())?"TEMPORAL":r.estado(),r.fechaExpiracion(),detalles,r.identityOwnerId());
    }
    public boolean reservaValida(Long id,Long cliente,Long evento){var r=consultarReserva(id);return cliente.equals(r.clienteId()) && evento.equals(r.eventoId()) && "TEMPORAL".equals(r.estado()) && r.fechaVencimiento().isAfter(LocalDateTime.now(Clock.systemUTC()));}
    public void confirmarReserva(Long id){RemoteCallSupport.mutate(RemoteCallSupport.Operation.RESERVATION_CONFIRM,()->confirmation.execute(()->{feignClient.confirm(id);return null;}));}
    public void liberarReserva(Long id){RemoteCallSupport.mutate(RemoteCallSupport.Operation.RESERVATION_RELEASE,()->feignClient.release(id));}
    public void compensarReserva(Long id){RemoteCallSupport.mutate(RemoteCallSupport.Operation.RESERVATION_COMPENSATE,()->feignClient.compensate(id));}
    public void liberarLugares(Long id,List<EntradaDTO> entradas){
        record Unidad(String entradaId,Long asientoId){}
        RemoteCallSupport.mutate(RemoteCallSupport.Operation.RESERVATION_RETURN,()->feignClient.giveBack(id,Map.of("entradas",entradas.stream().map(e->new Unidad(identidad.uuid(e.entradaId()),e.asientoId())).toList())));
    }
    public List<Long> consultarLugaresLiberados(Long id){
        String[] ids=RemoteCallSupport.read(RemoteCallSupport.Operation.RETURN_READ,()->reads.execute(()->{
            String[] result=feignClient.returned(id);
            RemoteCallSupport.require(result!=null && Arrays.stream(result).allMatch(FeignEntradaAdapter::validUuid) && Arrays.stream(result).distinct().count()==result.length);
            return result;
        }));
        return Arrays.stream(ids).map(identidad::registrar).toList();
    }
}
