package example.microservicio_inventario_reservas.integration;
import example.microservicio_inventario_reservas.dtos.*;
import example.microservicio_inventario_reservas.services.*;
import example.microservicio_inventario_reservas.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LimiteReservasTest {
 @Autowired ReservaService reservas;
 @Autowired InventarioService inventarios;
 @Autowired ReservaRepository repository;
 @Autowired DevolucionInventarioService devoluciones;
 @Autowired ControlEventoRepository controles;
 @Autowired LugarDevueltoRepository journal;
 @Autowired InventarioSectorRepository stock;
 long evento;
 @BeforeEach void setup(){evento=ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE);
  for(long sector:List.of(1L,2L))inventarios.inicializarInventario(evento,new InicializarInventarioRequest(sector,false,20,null));
  inventarios.publicar(evento);
 }
 @AfterEach void cleanup(){
  var own=repository.findAll().stream().filter(r->r.getIdEvento().equals(evento)).toList();
  for(var r:own)journal.deleteAll(journal.findByReservaIdReserva(r.getIdReserva()));
  repository.deleteAll(own);stock.deleteAll(stock.findAllByIdEventoOrderByIdSector(evento));controles.deleteById(evento);
 }
 ReservaResponse crear(int cantidad,long sector){return reservas.crearReservaTemporal(new CrearReservaRequest(1L,evento,sector,cantidad,null));}
 @Test void sieteSinComprasSeRechaza(){assertThrows(IllegalArgumentException.class,()->crear(7,1));}
 @Test void cuatroConfirmadasImpidenTresMas(){var r=crear(4,1);reservas.confirmarReserva(r.getIdReserva());assertThrows(IllegalArgumentException.class,()->crear(3,2));}
 @Test void seisConfirmadasImpidenOtra(){var r=crear(6,1);reservas.confirmarReserva(r.getIdReserva());assertThrows(IllegalArgumentException.class,()->crear(1,2));}
 @Test void pendientesVigentesCompartenCupoEntreSectores(){crear(4,1);crear(2,2);assertThrows(IllegalArgumentException.class,()->crear(1,1));}
 @Test void clientesDistintosNoCompartenCupo(){crear(6,1);assertDoesNotThrow(()->reservas.crearReservaTemporal(new CrearReservaRequest(2L,evento,2L,6,null)));}
 @Test void vencidaNoConsumeCupoAunqueSchedulerNoHayaCorrido(){var r=crear(6,1);var entity=repository.findById(r.getIdReserva()).orElseThrow();entity.setFechaExpiracion(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));repository.saveAndFlush(entity);assertDoesNotThrow(()->crear(6,2));}
 @Test void liberadaDevuelveCupo(){var r=crear(6,1);reservas.liberarReserva(r.getIdReserva());assertDoesNotThrow(()->crear(6,2));}
 @Test void confirmarNoCuentaDosVeces(){var r=crear(4,1);reservas.confirmarReserva(r.getIdReserva());reservas.confirmarReserva(r.getIdReserva());assertDoesNotThrow(()->crear(2,2));assertThrows(IllegalArgumentException.class,()->crear(1,1));}
 @Test void devolucionConfirmadaLiberaSoloSuCantidad(){var r=crear(6,1);reservas.confirmarReserva(r.getIdReserva());var req=new DevolverLugaresRequest(List.of(new DevolverLugaresRequest.Unidad("limite-"+evento,null)));devoluciones.devolver(r.getIdReserva(),req);devoluciones.devolver(r.getIdReserva(),req);assertDoesNotThrow(()->crear(1,2));assertThrows(IllegalArgumentException.class,()->crear(1,2));}
 @Test void compensacionDevuelveCupo(){var r=crear(6,1);reservas.confirmarReserva(r.getIdReserva());reservas.compensarReserva(r.getIdReserva());assertDoesNotThrow(()->crear(6,2));}
 @Test void concurrenciaEntreSectoresNoSuperaSeis()throws Exception{
  // Legacy inventories without a queue/control row must also serialize admission.
  controles.deleteById(evento);
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var results=new ArrayList<Future<Boolean>>();for(long s:List.of(1L,2L))results.add(pool.submit(()->{start.await();try{crear(4,s);return true;}catch(IllegalArgumentException e){return false;}}));start.countDown();int accepted=0;for(var f:results)if(f.get(15,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);assertEquals(4,repository.cupoOcupado(1L,evento,LocalDateTime.now(ZoneOffset.UTC)));}
  finally{pool.shutdownNow();}
 }
}
