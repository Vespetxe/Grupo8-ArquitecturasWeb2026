package example.microservicio_inventario_reservas.queue;
import example.microservicio_inventario_reservas.entities.ControlEvento;
import example.microservicio_inventario_reservas.enums.EstadoInventario;
import example.microservicio_inventario_reservas.repositories.*;
import example.microservicio_inventario_reservas.security.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static example.microservicio_inventario_reservas.queue.TurnoCola.Estado.*;

@Service @Transactional
public class ColaService {
 private final ControlEventoRepository eventos;
 private final TurnoColaRepository turnos;
 private final ReservaRepository reservas;
 private Clock clock=Clock.systemUTC();
 public ColaService(ControlEventoRepository eventos,TurnoColaRepository turnos,ReservaRepository reservas){this.eventos=eventos;this.turnos=turnos;this.reservas=reservas;}
 public record Configuracion(boolean activa,int tamanoTanda,int turnoSegundos){}
 public record EstadoTurno(boolean activa,String estado,Long numero,Long posicion,LocalDateTime venceEn,Long reservaId,Long clienteId,EstadoInventario estadoEvento){}
 private ControlEvento lock(Long evento){if(evento==null||evento<=0)throw new IllegalArgumentException("Evento invalido");return eventos.bloquear(evento).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Evento no encontrado"));}
 private Long actor(){Long id=CurrentActor.humanId();if(id==null||id<=0)throw new org.springframework.security.access.AccessDeniedException("Identidad humana requerida");return id;}
 public Configuracion configurar(Long evento,int tanda,int segundos){
  if(!CurrentActor.admin())throw new org.springframework.security.access.AccessDeniedException("Administrador requerido");
  if(tanda<1||tanda>100||segundos<30||segundos>600)throw new IllegalArgumentException("Tanda 1..100 y turno 30..600 segundos");
  var c=lock(evento);
  if(c.isColaActiva()){
   if(c.getColaTanda()!=tanda||c.getColaSegundos()!=segundos)throw new IllegalStateException("Configuracion de cola ya fijada");
  }else{
   if(c.getEstado()!=EstadoInventario.PREPARADO&&c.getEstado()!=EstadoInventario.HABILITADO)throw new IllegalStateException("Evento no disponible");
   if(reservas.existsByIdEvento(evento))throw new IllegalStateException("Configurar alta demanda antes de la primera reserva");
   c.activarCola(tanda,segundos);
  }
  return new Configuracion(true,c.getColaTanda(),c.getColaSegundos());
 }
 private void avanzar(Long evento,ControlEvento c){
  var ahora=LocalDateTime.now(clock);
  var activos=turnos.findByEventoAndEstadoOrderByNumeroAsc(evento,HABILITADO,PageRequest.of(0,100));
  for(var t:activos)if(!t.venceEn.isAfter(ahora))t.estado=EXPIRADO;
  // A new batch starts only after every member of the previous one finishes/expires/leaves.
  if(c.getEstado()!=EstadoInventario.HABILITADO||turnos.countByEventoAndEstado(evento,HABILITADO)>0)return;
  for(var t:turnos.findByEventoAndEstadoOrderByNumeroAsc(evento,ESPERANDO,PageRequest.of(0,c.getColaTanda()))){t.estado=HABILITADO;t.venceEn=ahora.plusSeconds(c.getColaSegundos());}
 }
 private EstadoTurno view(Long evento,ControlEvento c,TurnoCola t,Long cliente){
  Long posicion=null;
  if(t!=null&&t.estado==HABILITADO)posicion=0L;
  else if(t!=null&&t.estado==ESPERANDO)posicion=1+turnos.countByEventoAndEstadoAndNumeroLessThan(evento,ESPERANDO,t.numero);
  return new EstadoTurno(c.isColaActiva(),!c.isColaActiva()?"SIN_COLA":t==null?"NO_INSCRIPTO":t.estado.name(),t==null?null:t.numero,posicion,t==null?null:t.venceEn,t==null?null:t.reservaId,cliente,c.getEstado());
 }
 public EstadoTurno ingresar(Long evento){
  Long cliente=actor();var c=lock(evento);
  if(!c.isColaActiva())throw new IllegalStateException("El evento no tiene cola activa");
  if(c.getEstado()!=EstadoInventario.HABILITADO)throw new IllegalStateException("Venta no habilitada");
  avanzar(evento,c);
  var t=turnos.findByEventoAndCliente(evento,cliente).orElse(null);
  if(t==null||t.estado==ABANDONADO||t.estado==EXPIRADO){
   if(t==null){t=new TurnoCola();t.evento=evento;t.cliente=cliente;}
   t.numero=c.siguienteCola();t.estado=ESPERANDO;t.venceEn=null;t.reservaId=null;turnos.save(t);
  }
  avanzar(evento,c);return view(evento,c,t,cliente);
 }
 public EstadoTurno consultar(Long evento){Long cliente=actor();var c=lock(evento);if(c.isColaActiva())avanzar(evento,c);return view(evento,c,turnos.findByEventoAndCliente(evento,cliente).orElse(null),cliente);}
 public EstadoTurno abandonar(Long evento){
  Long cliente=actor();var c=lock(evento);var t=turnos.findByEventoAndCliente(evento,cliente).orElseThrow(CurrentActor::missing);
  if(t.estado==USADO)throw new IllegalStateException("El turno ya fue usado; no libera una reserva");
  t.estado=ABANDONADO;avanzar(evento,c);return view(evento,c,t,cliente);
 }
 // Joins the reservation transaction and takes the event lock BEFORE the sector lock.
 public TurnoCola admitirReserva(Long evento,Long cliente){
  var c=eventos.bloquear(evento).orElse(null);
  if(c==null||!c.isColaActiva())return null;
  CurrentActor.self(cliente);
  if(c.getEstado()!=EstadoInventario.HABILITADO)throw new IllegalStateException("Venta no habilitada");
  var t=turnos.findByEventoAndCliente(evento,actor()).orElseThrow(()->new IllegalStateException("Debe ingresar a la cola"));
  if(t.estado!=HABILITADO||!t.venceEn.isAfter(LocalDateTime.now(clock)))throw new IllegalStateException("Turno no habilitado o vencido; consulte su estado");
  return t;
 }
 public void consumir(TurnoCola turno,Long reserva){
  if(turno==null)return;
  var c=lock(turno.evento);turno.estado=USADO;turno.reservaId=reserva;avanzar(turno.evento,c);
 }
}
