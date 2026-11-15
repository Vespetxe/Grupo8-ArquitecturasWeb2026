package example.microservicio_inventario_reservas.queue;
import example.microservicio_inventario_reservas.entities.*;
import example.microservicio_inventario_reservas.enums.EstadoInventario;
import example.microservicio_inventario_reservas.repositories.*;
import example.microservicio_inventario_reservas.services.ReservaService;
import example.microservicio_inventario_reservas.dtos.CrearReservaRequest;
import example.microservicio_inventario_reservas.SecurityTestKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ColaIntegrationTest {
 @Autowired ColaService cola;
 @Autowired ReservaService reservas;
 @Autowired ControlEventoRepository controles;
 @Autowired InventarioSectorRepository inventario;
 @Autowired ReservaRepository repoReservas;
 @Autowired TurnoColaRepository turnos;
 @Autowired PlatformTransactionManager manager;
 @Autowired MockMvc mvc;
 static final AtomicLong IDS=new AtomicLong(90000);
 long evento;
 Instant now=Instant.parse("2030-01-01T00:00:00Z");
 @BeforeEach void fixture(){
  evento=IDS.incrementAndGet();
  clock(now);
  new TransactionTemplate(manager).execute(status->{controles.asegurar(evento);controles.bloquear(evento).orElseThrow().setEstado(EstadoInventario.HABILITADO);return null;});
  InventarioSector s=new InventarioSector();s.setIdEvento(evento);s.setIdSector(1L);s.setEsNumerado(false);s.setCapacidadTotal(20);s.setDisponibles(20);s.setEstado(EstadoInventario.HABILITADO);inventario.save(s);
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();ReflectionTestUtils.setField(cola,"clock",Clock.systemUTC());}
 void clock(Instant instant){ReflectionTestUtils.setField(cola,"clock",Clock.fixed(instant,ZoneOffset.UTC));}
 void actor(long id,String role){var jwt=Jwt.withTokenValue("unit-test-only").header("alg","RS256").subject(String.valueOf(id)).claim("token_use","user").claim("roles",List.of(role)).build();SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
 void client(long id){actor(id,"CLIENTE");}
 void activate(int size){actor(100,"ADMINISTRADOR");cola.configurar(evento,size,30);}
 ColaService.EstadoTurno join(long id){client(id);return cola.ingresar(evento);}
 ColaService.EstadoTurno turnStatus(long id){client(id);return cola.consultar(evento);}
 void reserve(long id){client(id);reservas.crearReservaTemporal(new CrearReservaRequest(id,evento,1L,1,null));}
 @Test void fifoDuplicatesAndRealBatches(){
  activate(2);
  assertEquals("HABILITADO",join(1).estado());
  var b=join(2);assertEquals(1,b.posicion());assertEquals(2,join(3).posicion());assertEquals(b.numero(),join(2).numero());
  client(2);assertThrows(IllegalStateException.class,()->reserve(2));
  reserve(1);assertEquals("USADO",turnStatus(1).estado());assertNotNull(turnStatus(1).reservaId());
  assertEquals("HABILITADO",turnStatus(2).estado());assertEquals("HABILITADO",turnStatus(3).estado());
  assertEquals("ESPERANDO",join(4).estado());reserve(2);assertEquals("ESPERANDO",turnStatus(4).estado());reserve(3);assertEquals("HABILITADO",turnStatus(4).estado());
  assertThrows(IllegalStateException.class,()->reserve(1));assertEquals(17,inventario.findByIdEventoAndIdSector(evento,1L).orElseThrow().getDisponibles());
 }
 <T> List<T> parallel(int n,java.util.function.IntFunction<T> action)throws Exception{
  var pool=Executors.newFixedThreadPool(n);var start=new CountDownLatch(1);List<Future<T>> futures=new ArrayList<>();
  try{for(int i=0;i<n;i++){final int index=i;futures.add(pool.submit(()->{start.await();try{return action.apply(index);}finally{SecurityContextHolder.clearContext();}}));}start.countDown();List<T> values=new ArrayList<>();for(var f:futures)values.add(f.get(25,TimeUnit.SECONDS));return values;}finally{pool.shutdownNow();}
 }
 @Test void concurrentJoinsAllocateUniqueOrderedPositions()throws Exception{
  activate(2);var entries=parallel(12,i->join(i+1));
  assertEquals(12,entries.stream().map(ColaService.EstadoTurno::numero).distinct().count());
  assertEquals(1,entries.stream().filter(t->t.estado().equals("HABILITADO")).count());
  assertEquals(java.util.stream.LongStream.rangeClosed(1,12).boxed().toList(),entries.stream().map(ColaService.EstadoTurno::numero).sorted().toList());
  for(var e:entries){var current=turnStatus(e.clienteId());assertEquals(e.numero(),current.numero());if(current.estado().equals("ESPERANDO"))assertEquals(current.numero()-1,current.posicion());}
 }
 @Test void concurrentDuplicateJoinHasOneRowAndOneNumber()throws Exception{
  activate(2);var entries=parallel(10,i->join(1));assertEquals(1,entries.stream().map(ColaService.EstadoTurno::numero).distinct().count());assertEquals(1,turnos.countByEventoAndEstado(evento,TurnoCola.Estado.HABILITADO));
 }
 @Test void expiryAdvancesBatchAndReentryGoesToTail(){
  activate(2);join(1);join(2);join(3);clock(now.plusSeconds(30));
  assertEquals("HABILITADO",turnStatus(2).estado());assertEquals("HABILITADO",turnStatus(3).estado());assertEquals("EXPIRADO",turnStatus(1).estado());
  assertThrows(IllegalStateException.class,()->reserve(1));assertEquals(4,join(1).numero());assertEquals("ESPERANDO",turnStatus(1).estado());
 }
 @Test void leavingDoesNotSkipRemainingMemberOrReleaseStock(){
  activate(2);join(1);join(2);join(3);client(1);cola.abandonar(evento);
  assertEquals("HABILITADO",turnStatus(2).estado());assertEquals("HABILITADO",turnStatus(3).estado());join(4);
  client(2);cola.abandonar(evento);cola.abandonar(evento);assertEquals("ESPERANDO",turnStatus(4).estado());client(3);cola.abandonar(evento);assertEquals("HABILITADO",turnStatus(4).estado());assertEquals(20,inventario.findByIdEventoAndIdSector(evento,1L).orElseThrow().getDisponibles());
 }
 @Test void failedReservationDoesNotConsumeTurn(){
  activate(1);join(1);client(1);assertThrows(RuntimeException.class,()->reservas.crearReservaTemporal(new CrearReservaRequest(1L,evento,999L,1,null)));
  assertEquals("HABILITADO",turnStatus(1).estado());reserve(1);assertEquals("USADO",turnStatus(1).estado());
 }
 @Test void concurrentReservationsCannotReuseTurnOrOversell()throws Exception{
  activate(2);join(1);var stock=inventario.findByIdEventoAndIdSector(evento,1L).orElseThrow();stock.setCapacidadTotal(1);stock.setDisponibles(1);inventario.save(stock);
  var success=parallel(10,i->{try{reserve(1);return true;}catch(IllegalStateException e){return false;}});
  assertEquals(1,success.stream().filter(Boolean::booleanValue).count());assertEquals(0,inventario.findByIdEventoAndIdSector(evento,1L).orElseThrow().getDisponibles());assertEquals(1,repoReservas.findByIdCliente(1L).stream().filter(r->r.getIdEvento().equals(evento)).count());
 }
 @Test void inactiveQueueKeepsLegacyContractAndActivationCannotBypassExistingSales(){
  assertFalse(turnStatus(1).activa());reserve(1);actor(100,"ADMINISTRADOR");assertThrows(IllegalStateException.class,()->cola.configurar(evento,2,30));
 }
 @Test void configurationIsControlledImmutableAndIdempotent(){
  client(1);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->cola.configurar(evento,2,30));
  activate(2);actor(100,"ADMINISTRADOR");assertTrue(cola.configurar(evento,2,30).activa());assertThrows(IllegalStateException.class,()->cola.configurar(evento,3,30));assertThrows(IllegalArgumentException.class,()->cola.configurar(evento,0,30));
 }
 @Test void suspensionAndCancellationNeverAdmitReservations(){
  activate(2);join(1);new TransactionTemplate(manager).execute(s->{controles.bloquear(evento).orElseThrow().setEstado(EstadoInventario.CANCELADO);return null;});
  assertThrows(IllegalStateException.class,()->reserve(1));assertThrows(IllegalStateException.class,()->join(2));assertEquals(EstadoInventario.CANCELADO,turnStatus(1).estadoEvento());
 }
 String jwt(long id,String role)throws Exception{return "Bearer "+SecurityTestKeys.token("inventario","user",String.valueOf(id),role,null,300);}
 @Test void realJwtOwnershipAndDirectBypassProtection()throws Exception{
  activate(2);String path="/colas/eventos/"+evento;
  mvc.perform(get(path+"/mi-turno")).andExpect(status().isUnauthorized());
  mvc.perform(get(path+"/mi-turno").header("Authorization","Bearer "+SecurityTestKeys.token("inventario","user","1","CLIENTE",null,-120))).andExpect(status().isUnauthorized());
  mvc.perform(post(path).header("Authorization","Bearer "+SecurityTestKeys.token("inventario","service","service:compras-to-inventario",null,"reservations.read",120))).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Authorization",jwt(1,"ORGANIZADOR"))).andExpect(status().isForbidden());
  mvc.perform(put(path).header("Authorization",jwt(1,"CLIENTE")).contentType("application/json").content("{\"tamanoTanda\":2,\"turnoSegundos\":30}")).andExpect(status().isForbidden());
  mvc.perform(post("/reservas").header("Authorization",jwt(1,"CLIENTE")).contentType("application/json").content("{\"idCliente\":1,\"idEvento\":"+evento+",\"idSector\":1,\"cantidad\":1}")).andExpect(status().isConflict());
  mvc.perform(post(path).header("Authorization",jwt(1,"CLIENTE"))).andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(1)).andExpect(jsonPath("$.estado").value("HABILITADO"));
  mvc.perform(get(path+"/mi-turno?clienteId=1").header("Authorization",jwt(2,"CLIENTE")).header("X-User-Id","1")).andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(2)).andExpect(jsonPath("$.estado").value("NO_INSCRIPTO"));
  mvc.perform(post("/reservas").header("Authorization",jwt(2,"CLIENTE")).contentType("application/json").content("{\"idCliente\":1,\"idEvento\":"+evento+",\"idSector\":1,\"cantidad\":1}")).andExpect(status().isForbidden());
  mvc.perform(get("/colas/eventos/999999999/mi-turno").header("Authorization",jwt(1,"CLIENTE"))).andExpect(status().isNotFound());
 }

 @Test void activationAndFirstReservationAreSerialized()throws Exception{
  var outcomes=parallel(2,n->{try{if(n==0){actor(100,"ADMINISTRADOR");cola.configurar(evento,2,30);}else reserve(1);return true;}catch(IllegalStateException e){return false;}});
  assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());
  boolean active=controles.findById(evento).orElseThrow().isColaActiva();
  assertEquals(!active,repoReservas.existsByIdEvento(evento));
 }
}
