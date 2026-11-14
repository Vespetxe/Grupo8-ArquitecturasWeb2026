package ar.edu.unicen.grupo8.comprasservice.service;

import ar.edu.unicen.grupo8.comprasservice.entity.enums.MedioPago;

import ar.edu.unicen.grupo8.comprasservice.client.*;
import ar.edu.unicen.grupo8.comprasservice.dto.*;
import ar.edu.unicen.grupo8.comprasservice.entity.*;
import ar.edu.unicen.grupo8.comprasservice.entity.enums.*;
import ar.edu.unicen.grupo8.comprasservice.exception.*;
import ar.edu.unicen.grupo8.comprasservice.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class FlujoCompraIntegracionTest {
    @Autowired CompraService compras;
    @Autowired PagoService pagos;
    @Autowired PagoRegistroService registro;
    @Autowired CompraRepository compraRepository;
    @Autowired PagoRepository pagoRepository;
    @Autowired ReintegroRepository reintegros;
    @Autowired DevolucionRepository devoluciones;
    @Autowired DetalleDevolucionRepository detallesDevolucion;
    @Autowired ControlLimiteEntradasRepository limites;
    @Autowired PlatformTransactionManager manager;
    @Autowired AlmacenSimulado almacen;
    @MockitoSpyBean ReservaClientSimulado reservas;
    @MockitoSpyBean EntradaClientSimulado entradas;
    @MockitoSpyBean PasarelaPagoSimuladaService pasarela;

    @BeforeEach void preparar() {
        reset(reservas, entradas, pasarela);
        pasarela.reiniciar(); reservas.reiniciarReservas(); entradas.reiniciarEntradas();
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            reintegros.deleteAll(); detallesDevolucion.deleteAll(); devoluciones.deleteAll();
            pagoRepository.deleteAll(); compraRepository.deleteAll(); limites.deleteAll();
        });
    }
    private Compra crear() {
        CrearCompraDTO dto = new CrearCompraDTO(); dto.setClienteId(10L); dto.setEventoId(20L); dto.setReservaId(30L);
        DetalleCompraDTO d = new DetalleCompraDTO(); d.setSectorId(1L); d.setCantidad(2); dto.setDetalles(List.of(d));
        return compras.crearCompra(dto);
    }
    private Compra consultar(Compra c) { return compras.obtenerCompraPorId(c.getId()); }
    private void expirarTurno(Compra c) {
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            var entidad = compraRepository.findById(c.getId()).orElseThrow();
            entidad.setBloqueoHasta(LocalDateTime.now(Clock.systemUTC()).minusSeconds(1));
        });
    }
    private void sinTransaccion() { assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "No mantener transacciones locales durante llamadas externas"); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard.Family.class,names={"RESERVATION_CONFIRM","TICKETS_ISSUE"})
    void criticalWriteNotDispatchedRetainsPaidStageAndRecoversSameIdentity(ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard.Family family){
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("ticketya.resilience.read.window","2").withProperty("ticketya.resilience.read.minimum","2");
        var gate=new ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard(env,family);
        for(int i=0;i<2;i++)assertThrows(org.springframework.web.client.RestClientException.class,()->gate.execute(()->{throw new org.springframework.web.client.ResourceAccessException("controlled unavailable");}));
        verifyCriticalAdmissionAndRecovery(family,gate,"CIRCUIT_OPEN",()->((io.github.resilience4j.circuitbreaker.CircuitBreaker)org.springframework.test.util.ReflectionTestUtils.getField(gate,"circuit")).transitionToClosedState());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard.Family.class,names={"RESERVATION_CONFIRM","TICKETS_ISSUE"})
    void saturatedCriticalWriteDoesNotCompensateAndRecoversSamePayment(ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard.Family family){
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("ticketya.resilience.read.concurrent","1");
        var gate=new ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard(env,family);
        var bulk=(io.github.resilience4j.bulkhead.Bulkhead)org.springframework.test.util.ReflectionTestUtils.getField(gate,"bulkhead");assertTrue(bulk.tryAcquirePermission());
        try{verifyCriticalAdmissionAndRecovery(family,gate,"BULKHEAD_FULL",bulk::onComplete);}finally{if(bulk.getMetrics().getAvailableConcurrentCalls()==0)bulk.onComplete();}
    }
    private void verifyCriticalAdmissionAndRecovery(ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard.Family family,ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard gate,String category,Runnable recover){
        Compra c=crear();var effects=new java.util.concurrent.atomic.AtomicInteger();var blocked=new java.util.concurrent.atomic.AtomicReference<ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard.Unavailable>();
        org.mockito.stubbing.Answer<Object> call=i->{try{gate.execute(()->null);}catch(ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard.Unavailable e){blocked.set(e);throw e;}effects.incrementAndGet();return i.callRealMethod();};
        boolean confirmation=family==ar.edu.unicen.grupo8.comprasservice.resilience.PilotReadGuard.Family.RESERVATION_CONFIRM;
        if(confirmation)doAnswer(call).when(reservas).confirmarReserva(30L);else doAnswer(call).when(entradas).solicitarEntradas(any(),any(),any(),anyList());
        Pago pago=pagos.procesarPago(c.getId(),"critical-"+family,MedioPago.TARJETA_DEBITO);
        assertEquals(category,blocked.get().category());assertFalse(blocked.get().dispatched());assertEquals(0,effects.get());
        assertEquals(EstadoPago.APROBADO,pago.getEstado());assertEquals(EstadoCompra.PROCESANDO,consultar(c).getEstado());
        assertEquals(confirmation?EtapaCompra.CONFIRMACION:EtapaCompra.EMISION,consultar(c).getEtapa());
        assertEquals(0,reintegros.count());verify(pasarela,never()).reintegrarPago(any(),any());verify(reservas,never()).liberarReserva(any());verify(reservas,never()).compensarReserva(any());verify(entradas,never()).anularEntradas(any());
        recover.run();assertEquals(pago.getId(),pagos.procesarPago(c.getId(),"critical-"+family,MedioPago.TARJETA_DEBITO).getId());
        pagos.procesarPago(c.getId(),"critical-"+family,MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoCompra.COMPLETADA,consultar(c).getEstado());assertEquals(30L,consultar(c).getReservaId());assertEquals(2,consultar(c).getEntradasIds().size());assertEquals(1,effects.get());assertEquals(1,pagoRepository.count());assertEquals(0,reintegros.count());verify(pasarela,times(1)).procesarPago(any(),any());
    }

    @Test void exhaustedAfterChargePersistsConfirmationWithoutCompensationAndRecoversSamePayment(){
        Compra c=crear();
        doAnswer(i->{var result=i.callRealMethod();var request=new org.springframework.mock.web.MockHttpServletRequest();ar.edu.unicen.grupo8.comprasservice.timing.RequestBudget.start(request,0);org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));return result;}).when(pasarela).procesarPago(any(),any());
        Pago p;
        try{p=pagos.procesarPago(c.getId(),"budget-after-charge",MedioPago.TARJETA_DEBITO);assertEquals(EstadoPago.APROBADO,p.getEstado());assertEquals(EtapaCompra.CONFIRMACION,consultar(c).getEtapa());verify(reservas,never()).confirmarReserva(any());verify(pasarela,never()).reintegrarPago(any(),any());}
        finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
        assertEquals(p.getId(),pagos.procesarPago(c.getId(),"budget-after-charge",MedioPago.TARJETA_DEBITO).getId());assertEquals(EstadoCompra.COMPLETADA,consultar(c).getEstado());verify(pasarela,times(1)).procesarPago(any(),any());assertEquals(0,reintegros.count());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={401,403,404,409,422,429,500,502,503,504})
    void remoteConfirmationFailureNeverRefundsOrReleasesAndRecoversOriginalPayment(int status){
        Compra c=crear();
        doAnswer(i->{RestSupport.mutate(RestSupport.Operation.RESERVATION_CONFIRM,()->{throw remote(status);});return null;}).when(reservas).confirmarReserva(30L);
        Pago p=pagos.procesarPago(c.getId(),"classification-confirm",MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.APROBADO,p.getEstado());assertEquals(EtapaCompra.CONFIRMACION,consultar(c).getEtapa());
        assertEquals(EstadoCompra.PROCESANDO,consultar(c).getEstado());assertEquals(0,reintegros.count());
        verify(pasarela,never()).reintegrarPago(any(),any());verify(reservas,never()).compensarReserva(any());verify(reservas,never()).liberarReserva(any());
        verify(entradas,never()).anularEntradas(any());
        doCallRealMethod().when(reservas).confirmarReserva(30L);
        assertEquals(p.getId(),pagos.procesarPago(c.getId(),"classification-confirm",MedioPago.TARJETA_DEBITO).getId());
        assertEquals(EstadoCompra.COMPLETADA,consultar(c).getEstado());assertEquals(1,pagoRepository.count());assertEquals(0,reintegros.count());
        verify(pasarela,times(1)).procesarPago(any(),any());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={401,403,404,409,422,429,500,502,503,504})
    void remoteEmissionFailureNeverCompensatesAndRecoversOriginalBatch(int status){
        Compra c=crear();
        doAnswer(i->{RestSupport.mutate(RestSupport.Operation.TICKETS_ISSUE,()->{throw remote(status);});return null;}).when(entradas).solicitarEntradas(any(),any(),any(),anyList());
        Pago p=pagos.procesarPago(c.getId(),"classification-issue",MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.APROBADO,p.getEstado());assertEquals(EtapaCompra.EMISION,consultar(c).getEtapa());
        assertEquals(0,reintegros.count());verify(pasarela,never()).reintegrarPago(any(),any());verify(entradas,never()).anularEntradas(any());
        verify(reservas,never()).compensarReserva(any());verify(reservas,never()).liberarReserva(any());
        doCallRealMethod().when(entradas).solicitarEntradas(any(),any(),any(),anyList());
        assertEquals(p.getId(),pagos.procesarPago(c.getId(),"classification-issue",MedioPago.TARJETA_DEBITO).getId());
        pagos.procesarPago(c.getId(),"classification-issue",MedioPago.TARJETA_DEBITO);
        assertEquals(2,consultar(c).getEntradasIds().size());assertEquals(1,pagoRepository.count());assertEquals(0,reintegros.count());verify(pasarela,times(1)).procesarPago(any(),any());
    }
    private org.springframework.web.client.RestClientResponseException remote(int status){
        return new org.springframework.web.client.RestClientResponseException("sentinel-secret",status,"failure",new org.springframework.http.HttpHeaders(),new byte[0],java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test void contradictoryReservationSnapshotAfterChargeIsUnknownNotAReasonToRefund(){
        Compra c=crear();var original=reservas.consultarReserva(30L);
        doAnswer(i->{var charged=i.callRealMethod();doReturn(new ReservaDTO(original.reservaId(),999L,original.eventoId(),original.estado(),original.fechaVencimiento(),original.detalles())).when(reservas).consultarReserva(30L);return charged;}).when(pasarela).procesarPago(any(),any());
        Pago p=pagos.procesarPago(c.getId(),"contradictory",MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.APROBADO,p.getEstado());assertEquals(EtapaCompra.CONFIRMACION,consultar(c).getEtapa());assertEquals(0,reintegros.count());
        verify(pasarela,never()).reintegrarPago(any(),any());verify(reservas,never()).compensarReserva(any());
        doCallRealMethod().when(reservas).consultarReserva(30L);
        assertEquals(p.getId(),pagos.procesarPago(c.getId(),"contradictory",MedioPago.TARJETA_DEBITO).getId());assertEquals(EstadoCompra.COMPLETADA,consultar(c).getEstado());verify(pasarela,times(1)).procesarPago(any(),any());
    }

    @Test void exitoPersisteTodosLosEstadosSinTransaccionExterna() {
        doAnswer(i -> { sinTransaccion(); return i.callRealMethod(); }).when(pasarela).procesarPago(any(), any());
        doAnswer(i -> { sinTransaccion(); return i.callRealMethod(); }).when(reservas).confirmarReserva(any());
        doAnswer(i -> { sinTransaccion(); return i.callRealMethod(); }).when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        Compra c = crear(); Pago p = pagos.procesarPago(c.getId(), "exito", MedioPago.TARJETA_DEBITO);
        Compra finalizada = consultar(c);
        assertEquals(EstadoCompra.COMPLETADA, finalizada.getEstado()); assertEquals(EtapaCompra.FINALIZADA, finalizada.getEtapa());
        assertEquals(EstadoPago.APROBADO, pagos.obtenerPagoPorId(p.getId()).getEstado());
        assertEquals(2, finalizada.getEntradasIds().size()); assertEquals("CONFIRMADA", reservas.consultarReserva(30L).estado());
        pagos.procesarPago(c.getId(), "exito", MedioPago.TARJETA_DEBITO);
        verify(pasarela, times(1)).procesarPago(any(), any()); verify(reservas, times(1)).confirmarReserva(any());
        verify(entradas, times(1)).solicitarEntradas(any(), any(), any(), anyList());
    }
    @Test void timeoutDePasarelaSeRecuperaSinNuevoCobro() {
        Compra c = crear(); Pago p = pagos.procesarPago(c.getId(), "simular-timeout", MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.RESULTADO_DESCONOCIDO, pagos.obtenerPagoPorId(p.getId()).getEstado());
        assertEquals(EstadoCompra.PROCESANDO, consultar(c).getEstado());
        assertThrows(IllegalStateException.class, () -> pagos.procesarPago(c.getId(), "otra", MedioPago.TARJETA_DEBITO));
        assertFalse(pagos.procesarPagoConResultado(c.getId(), "simular-timeout", MedioPago.TARJETA_DEBITO).creado());
        assertEquals(EstadoCompra.COMPLETADA, consultar(c).getEstado()); verify(pasarela, times(1)).procesarPago(any(), any());
    }
    @Test void resultadoTodaviaDesconocidoNoGeneraOtroCobro() {
        Compra c = crear(); pagos.procesarPago(c.getId(), "simular-timeout", MedioPago.TARJETA_DEBITO);
        doReturn(null).when(pasarela).consultarPago("simular-timeout");
        Pago p = pagos.procesarPago(c.getId(), "simular-timeout", MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.RESULTADO_DESCONOCIDO, p.getEstado()); assertEquals(EtapaCompra.PAGO, consultar(c).getEtapa());
        verify(pasarela, times(1)).procesarPago(any(), any());
    }
    @Test void confirmacionRealizadaConRespuestaPerdidaNoSeDuplica() {
        Compra c = crear();
        doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("Respuesta perdida"); }).when(reservas).confirmarReserva(30L);
        Pago p = pagos.procesarPago(c.getId(), "confirmacion", MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.APROBADO, pagoRepository.findById(p.getId()).orElseThrow().getEstado());
        assertEquals(EtapaCompra.CONFIRMACION, consultar(c).getEtapa());
        pagos.procesarPago(c.getId(), "confirmacion", MedioPago.TARJETA_DEBITO); assertEquals(EstadoCompra.COMPLETADA, consultar(c).getEstado());
        verify(reservas, times(1)).confirmarReserva(any()); verify(pasarela, never()).reintegrarPago(any(), any());
    }
    @Test void emisionRealizadaConRespuestaPerdidaSeConsultaSinReemitir() {
        Compra c = crear();
        doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("Respuesta perdida"); })
                .when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        pagos.procesarPago(c.getId(), "emision", MedioPago.TARJETA_DEBITO); assertEquals(EtapaCompra.EMISION, consultar(c).getEtapa());
        assertEquals(2, entradas.consultarEntradas(c.getId()).size());
        pagos.procesarPago(c.getId(), "emision", MedioPago.TARJETA_DEBITO); assertEquals(2, consultar(c).getEntradasIds().size());
        assertEquals(EstadoCompra.COMPLETADA, consultar(c).getEstado());
        verify(entradas, times(1)).solicitarEntradas(any(), any(), any(), anyList()); verify(pasarela, never()).reintegrarPago(any(), any());
    }
    @Test void fallaAntesDeEmitirPermiteRecuperacionConIdentidadEstable() {
        Compra c = crear();
        doThrow(new IllegalStateException("Sin comunicacion")).doCallRealMethod().when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        pagos.procesarPago(c.getId(), "antes-emision", MedioPago.TARJETA_DEBITO); assertEquals(EtapaCompra.EMISION, consultar(c).getEtapa());
        pagos.procesarPago(c.getId(), "antes-emision", MedioPago.TARJETA_DEBITO); assertEquals(EstadoCompra.COMPLETADA, consultar(c).getEstado());
        assertEquals(2, entradas.consultarEntradas(c.getId()).size()); verify(pasarela, times(1)).procesarPago(any(), any());
    }
    @Test void rechazoDefinitivoDeReservaPersisteCompensacion() {
        Compra c = crear(); doThrow(new OperacionExternaRechazadaException("Vencio durante el pago")).when(reservas).confirmarReserva(30L);
        Pago p = pagos.procesarPago(c.getId(), "reserva-rechazada", MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.REINTEGRADO_TOTAL, pagos.obtenerPagoPorId(p.getId()).getEstado());
        assertEquals(EstadoCompra.FALLIDA, consultar(c).getEstado());
        assertEquals(EstadoReintegro.COMPLETADO, reintegros.findByClaveIdempotencia("compensacion:" + p.getId()).orElseThrow().getEstado());
        assertEquals("LIBERADA", reservas.consultarReserva(30L).estado());
        pagos.procesarPago(c.getId(), "reserva-rechazada", MedioPago.TARJETA_DEBITO); verify(pasarela, times(1)).reintegrarPago(any(), any());
    }
    @Test void rechazoDefinitivoDeEmisionCompensaYLiberarConfirmacion() {
        Compra c = crear(); doThrow(new OperacionExternaRechazadaException("No puede emitir")).when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        Pago p = pagos.procesarPago(c.getId(), "emision-rechazada", MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.REINTEGRADO_TOTAL, p.getEstado()); assertEquals(EstadoCompra.FALLIDA, consultar(c).getEstado());
        assertEquals("LIBERADA", reservas.consultarReserva(30L).estado()); assertTrue(entradas.consultarEntradas(c.getId()).isEmpty());
    }
    @Test void reintegroRealizadoConRespuestaPerdidaQuedaPersistidoYSeRecupera() {
        Compra c = crear(); doThrow(new OperacionExternaRechazadaException("No emite")).when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        doAnswer(i -> { i.callRealMethod(); throw new IllegalStateException("Respuesta perdida"); }).when(pasarela).reintegrarPago(any(), any());
        Pago p = pagos.procesarPago(c.getId(), "reintegro-timeout", MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.REINTEGRO_DESCONOCIDO, pagos.obtenerPagoPorId(p.getId()).getEstado());
        assertEquals(EstadoReintegro.RESULTADO_DESCONOCIDO, reintegros.findByClaveIdempotencia("compensacion:" + p.getId()).orElseThrow().getEstado());
        pagos.procesarPago(c.getId(), "reintegro-timeout", MedioPago.TARJETA_DEBITO); assertEquals(EstadoCompra.FALLIDA, consultar(c).getEstado());
        assertEquals(EstadoPago.REINTEGRADO_TOTAL, pagos.obtenerPagoPorId(p.getId()).getEstado());
        verify(pasarela, times(1)).reintegrarPago(any(), any()); assertEquals(1, reintegros.count());
    }
    @Test void rechazoDePagoYLiberacionFallidaSeRecuperanSinNuevoPago() {
        Compra c = crear(); new TransactionTemplate(manager).executeWithoutResult(s -> compraRepository.findById(c.getId()).orElseThrow().setImporteTotal(new BigDecimal("150000")));
        doThrow(new IllegalStateException("Sin comunicacion")).doCallRealMethod().when(reservas).liberarReserva(30L);
        Pago p = pagos.procesarPago(c.getId(), "rechazo", MedioPago.TARJETA_DEBITO); assertEquals(EstadoPago.RECHAZADO, p.getEstado());
        assertEquals(EtapaCompra.LIBERACION, consultar(c).getEtapa());
        pagos.procesarPago(c.getId(), "rechazo", MedioPago.TARJETA_DEBITO); assertEquals(EstadoCompra.FALLIDA, consultar(c).getEstado());
        verify(pasarela, times(1)).procesarPago(any(), any()); verify(entradas, never()).solicitarEntradas(any(), any(), any(), anyList());
    }
    @Test void respuestaIncompletaDeEmisionNuncaCompletaCompra() {
        Compra c = crear(); doReturn(List.of()).when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        pagos.procesarPago(c.getId(), "incompleta", MedioPago.TARJETA_DEBITO); assertEquals(EstadoCompra.PROCESANDO, consultar(c).getEstado());
        assertEquals(EtapaCompra.EMISION, consultar(c).getEtapa()); assertTrue(consultar(c).getEntradasIds().isEmpty());
    }
    @Test void turnoAbandonadoAntesDeCobrarSeRecuperaSinCrearOtroPago() {
        Compra c = crear(); registro.tomar(c.getId(), "abandonado", MedioPago.TARJETA_DEBITO); expirarTurno(c);
        pagos.procesarPago(c.getId(), "abandonado", MedioPago.TARJETA_DEBITO); assertEquals(EstadoCompra.COMPLETADA, consultar(c).getEstado());
        assertEquals(1, pagoRepository.count()); verify(pasarela, times(1)).procesarPago(any(), any());
    }
    @Test void caidaDespuesDeCobrarAntesDePersistirResultadoConsultaDiario() {
        Compra c = crear(); var turno = registro.tomar(c.getId(), "caida", MedioPago.TARJETA_DEBITO);
        pasarela.procesarPago("caida", turno.pago().getImporte()); expirarTurno(c);
        pagos.procesarPago(c.getId(), "caida", MedioPago.TARJETA_DEBITO); assertEquals(EstadoCompra.COMPLETADA, consultar(c).getEstado());
        verify(pasarela, times(1)).procesarPago(any(), any()); assertEquals(1, pagoRepository.count());
    }
    @Test void simuladoresReconstruidosConservanResultados() {
        Compra c = crear(); Pago p = pagos.procesarPago(c.getId(), "durable", MedioPago.TARJETA_DEBITO);
        var nuevaPasarela = new PasarelaPagoSimuladaService(almacen);
        var nuevasEntradas = new EntradaClientSimulado(almacen);
        var nuevasReservas = new ReservaClientSimulado(almacen, Clock.systemUTC());
        assertEquals(p.getReferenciaExterna(), nuevaPasarela.consultarPago("durable").getReferenciaExterna());
        assertIterableEquals(consultar(c).getEntradasIds(), nuevasEntradas.consultarEntradas(c.getId()).stream().map(EntradaDTO::entradaId).toList());
        assertEquals("CONFIRMADA", nuevasReservas.consultarReserva(30L).estado());
    }

    @Test void anulacionDurableImpideEmisionPosteriorAlReintegro() {
        Compra c = crear();
        doThrow(new OperacionExternaRechazadaException("No emite")).when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        pagos.procesarPago(c.getId(), "barrera", MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoCompra.FALLIDA, consultar(c).getEstado());
        var nuevaInstancia = new EntradaClientSimulado(almacen);
        assertThrows(OperacionExternaRechazadaException.class, () -> nuevaInstancia.solicitarEntradas(c.getId(), 20L, 10L,
                List.of(new DetalleEmisionDTO(1L, null, 2))));
        assertTrue(nuevaInstancia.consultarEntradas(c.getId()).isEmpty());
    }
    @Test void reservaRepetidaConcurrentementeProduceUnaSolaCompra() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1); ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> accion = () -> { inicio.await(); try { crear(); return true; } catch (IllegalStateException e) { return false; } };
            Future<Boolean> a = ejecutor.submit(accion), b = ejecutor.submit(accion); inicio.countDown();
            assertNotEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)); assertEquals(1, compraRepository.count());
        } finally { ejecutor.shutdownNow(); }
    }
    @Test void mismaClaveEnComprasDiferentesNuncaCobraDosVeces() throws Exception {
        Compra a = crear();
        CrearCompraDTO dto = new CrearCompraDTO(); dto.setClienteId(11L); dto.setEventoId(20L); dto.setReservaId(32L);
        DetalleCompraDTO d = new DetalleCompraDTO(); d.setSectorId(1L); d.setCantidad(1); dto.setDetalles(List.of(d)); Compra b = compras.crearCompra(dto);
        CountDownLatch inicio = new CountDownLatch(1); ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> primero = ejecutor.submit(() -> intentarPago(a, inicio)); Future<Boolean> segundo = ejecutor.submit(() -> intentarPago(b, inicio));
            inicio.countDown(); assertNotEquals(primero.get(20, TimeUnit.SECONDS), segundo.get(20, TimeUnit.SECONDS));
            assertEquals(1, pagoRepository.count()); verify(pasarela, times(1)).procesarPago(any(), any());
        } finally { ejecutor.shutdownNow(); }
    }
    private boolean intentarPago(Compra c, CountDownLatch inicio) throws Exception {
        inicio.await(); try { pagos.procesarPago(c.getId(), "clave-global", MedioPago.TARJETA_DEBITO); return true; }
        catch (IllegalStateException | org.springframework.dao.DataIntegrityViolationException ex) { return false; }
    }

    @Test void reservaVencidaImpideCobroSinRegistrarPago() {
        Compra c = crear();
        reservas.reiniciarReservas();
        reservas.registrarReserva(new ReservaDTO(30L, 10L, 20L, "TEMPORAL", LocalDateTime.now(Clock.systemUTC()).minusSeconds(1),
                List.of(new DetalleEmisionDTO(1L, null, 2))));
        assertThrows(IllegalArgumentException.class, () -> pagos.procesarPago(c.getId(), "vencida", MedioPago.TARJETA_DEBITO));
        assertEquals(0, pagoRepository.count()); verify(pasarela, never()).procesarPago(any(), any());
    }

    @Test void pendienteVencidaDejaDeConsumirLimiteAlCrearOtraCompra() {
        Compra anterior = crear();
        new TransactionTemplate(manager).executeWithoutResult(s -> compraRepository.findById(anterior.getId()).orElseThrow()
                .setReservaVencimiento(LocalDateTime.now(Clock.systemUTC()).minusSeconds(1)));
        reservas.registrarReserva(new ReservaDTO(900L, 10L, 20L, "TEMPORAL", LocalDateTime.now(Clock.systemUTC()).plusMinutes(15),
                List.of(new DetalleEmisionDTO(1L, null, 6))));
        CrearCompraDTO dto = new CrearCompraDTO(); dto.setClienteId(10L); dto.setEventoId(20L); dto.setReservaId(900L);
        DetalleCompraDTO detalle = new DetalleCompraDTO(); detalle.setSectorId(1L); detalle.setCantidad(6); dto.setDetalles(List.of(detalle));
        Compra nueva = compras.crearCompra(dto);
        assertEquals(EstadoCompra.PENDIENTE, nueva.getEstado()); assertEquals(EstadoCompra.FALLIDA, consultar(anterior).getEstado());
    }

    @Test void compensacionNoReintegraHastaConfirmarAnulacion() {
        Compra c = crear();
        doThrow(new OperacionExternaRechazadaException("No emite")).when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        doThrow(new IllegalStateException("Sin comunicacion")).doCallRealMethod().when(entradas).anularEntradas(any());
        Pago p = pagos.procesarPago(c.getId(), "anulacion", MedioPago.TARJETA_DEBITO);
        assertEquals(EtapaCompra.COMPENSACION, consultar(c).getEtapa());
        assertEquals(EstadoPago.APROBADO, p.getEstado()); verify(pasarela, never()).reintegrarPago(any(), any());
        pagos.procesarPago(c.getId(), "anulacion", MedioPago.TARJETA_DEBITO); assertEquals(EstadoCompra.FALLIDA, consultar(c).getEstado());
        verify(pasarela, times(1)).reintegrarPago(any(), any());
    }

    @Test void catalogoSimuladoRechazaEventosNoDisponiblesAntesDeRegistrarCompra() {
        for (long evento : new long[]{901, 902, 903, 999}) {
            CrearCompraDTO dto = new CrearCompraDTO(); dto.setClienteId(10L); dto.setEventoId(evento); dto.setReservaId(30L);
            DetalleCompraDTO d = new DetalleCompraDTO(); d.setSectorId(1L); d.setCantidad(2); dto.setDetalles(List.of(d));
            assertThrows(IllegalArgumentException.class, () -> compras.crearCompra(dto));
        }
        assertEquals(0, compraRepository.count());
        verify(reservas, never()).reservaValida(any(), any(), any());
    }

    @Test void cancelacionConRespuestaPerdidaSeRecuperaSinRepetirLiberacion() {
        Compra c = crear();
        doAnswer(i -> { sinTransaccion(); i.callRealMethod(); throw new IllegalStateException("Respuesta perdida"); })
                .when(reservas).liberarReserva(30L);
        Compra cancelada = compras.cancelarCompra(c.getId());
        assertEquals(EstadoCompra.CANCELADA, cancelada.getEstado());
        assertEquals(EtapaCompra.LIBERACION, consultar(c).getEtapa());
        assertThrows(IllegalStateException.class, () -> pagos.procesarPago(c.getId(), "cancelada", MedioPago.TARJETA_DEBITO));
        compras.cancelarCompra(c.getId());
        assertEquals(EtapaCompra.FINALIZADA, consultar(c).getEtapa());
        assertEquals("LIBERADA", reservas.consultarReserva(30L).estado());
        verify(reservas, times(1)).liberarReserva(30L);
        assertEquals(0, pagoRepository.count()); verify(pasarela, never()).procesarPago(any(), any());
    }

    @Test void cancelacionConFalloAntesDeLiberarSePuedeReintentar() {
        Compra c = crear();
        doThrow(new IllegalStateException("Sin comunicacion")).doCallRealMethod().when(reservas).liberarReserva(30L);
        compras.cancelarCompra(c.getId()); assertEquals(EtapaCompra.LIBERACION, consultar(c).getEtapa());
        assertEquals("TEMPORAL", reservas.consultarReserva(30L).estado());
        compras.cancelarCompra(c.getId()); assertEquals(EstadoCompra.CANCELADA, consultar(c).getEstado());
        assertEquals(EtapaCompra.FINALIZADA, consultar(c).getEtapa());
        assertEquals("LIBERADA", reservas.consultarReserva(30L).estado());
    }

    @Test void pagoYCancelacionSimultaneosSoloPermitenUnaOperacion() throws Exception {
        Compra c = crear(); CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> pago = ejecutor.submit(() -> {
                inicio.await();
                try { pagos.procesarPago(c.getId(), "carrera-cancelacion", MedioPago.TARJETA_DEBITO); return true; }
                catch (IllegalStateException | IllegalArgumentException e) { return false; }
            });
            Future<Boolean> cancelacion = ejecutor.submit(() -> {
                inicio.await();
                try { compras.cancelarCompra(c.getId()); return true; }
                catch (IllegalStateException e) { return false; }
            });
            inicio.countDown(); boolean pagada = pago.get(20, TimeUnit.SECONDS), cancelada = cancelacion.get(20, TimeUnit.SECONDS);
            assertNotEquals(pagada, cancelada);
            assertEquals(pagada ? EstadoCompra.COMPLETADA : EstadoCompra.CANCELADA, consultar(c).getEstado());
            assertEquals(pagada ? 1 : 0, pagoRepository.count());
            assertEquals(pagada ? "CONFIRMADA" : "LIBERADA", reservas.consultarReserva(30L).estado());
            assertEquals(pagada ? 2 : 0, entradas.consultarEntradas(c.getId()).size());
        } finally { ejecutor.shutdownNow(); }
    }

    @Test void cancelacionRechazaComprasProcesandoYCompletadas() {
        Compra c = crear(); pagos.procesarPago(c.getId(), "simular-timeout", MedioPago.TARJETA_DEBITO);
        assertThrows(IllegalStateException.class, () -> compras.cancelarCompra(c.getId()));
        pagos.procesarPago(c.getId(), "simular-timeout", MedioPago.TARJETA_DEBITO);
        assertThrows(IllegalStateException.class, () -> compras.cancelarCompra(c.getId()));
        assertEquals(EstadoCompra.COMPLETADA, consultar(c).getEstado());
        verify(reservas, never()).liberarReserva(any());
    }

    @Test void reintegroRechazadoEsDefinitivoNoDesconocidoYNuncaSeReenvia() {
        Compra c = crear();
        doThrow(new OperacionExternaRechazadaException("No emite")).when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        doReturn(new ResultadoPasarela(false, "REINT-RECHAZADO")).when(pasarela).reintegrarPago(any(), any());
        assertThrows(IllegalStateException.class, () -> pagos.procesarPago(c.getId(), "reintegro-rechazado", MedioPago.TARJETA_DEBITO));
        Pago p = pagoRepository.findByCompraId(c.getId()).get(0);
        var r = reintegros.findByClaveIdempotencia("compensacion:" + p.getId()).orElseThrow();
        assertEquals(EstadoReintegro.RECHAZADO, r.getEstado()); assertNotNull(r.getFechaProcesamiento());
        assertEquals(EstadoPago.APROBADO, p.getEstado()); assertEquals(0, p.getImporteReintegrado().signum());
        assertEquals(EstadoCompra.PROCESANDO, consultar(c).getEstado()); assertEquals(EtapaCompra.COMPENSACION, consultar(c).getEtapa());
        assertNull(consultar(c).getEjecutor());
        assertThrows(IllegalStateException.class, () -> pagos.procesarPago(c.getId(), "reintegro-rechazado", MedioPago.TARJETA_DEBITO));
        verify(pasarela, times(1)).procesarPago(any(), any()); verify(pasarela, times(1)).reintegrarPago(any(), any());
        verify(reservas, never()).compensarReserva(any());
    }

    @Test void reintegroDesconocidoLuegoRechazadoRecuperaEstadoCobrado() {
        Compra c = crear();
        doThrow(new OperacionExternaRechazadaException("No emite")).when(entradas).solicitarEntradas(any(), any(), any(), anyList());
        doThrow(new IllegalStateException("Sin respuesta")).when(pasarela).reintegrarPago(any(), any());
        Pago p = pagos.procesarPago(c.getId(), "reintegro-pendiente", MedioPago.TARJETA_DEBITO);
        assertEquals(EstadoPago.REINTEGRO_DESCONOCIDO, p.getEstado());
        doReturn(new ResultadoPasarela(false, "REINT-RECHAZADO")).when(pasarela).consultarReintegro("reintegro-pendiente");
        assertThrows(IllegalStateException.class, () -> pagos.procesarPago(c.getId(), "reintegro-pendiente", MedioPago.TARJETA_DEBITO));
        assertEquals(EstadoPago.APROBADO, pagos.obtenerPagoPorId(p.getId()).getEstado());
        assertEquals(EstadoReintegro.RECHAZADO, reintegros.findByClaveIdempotencia("compensacion:" + p.getId()).orElseThrow().getEstado());
        verify(pasarela, times(1)).reintegrarPago(any(), any());
    }

    @Test void pagoHistoricoPendientePuedeRecuperarseSinAtribuirUnMedioNuevo() {
        Compra c = crear(); var turno = registro.tomar(c.getId(), "historico-pendiente", MedioPago.TARJETA_DEBITO);
        new TransactionTemplate(manager).executeWithoutResult(s -> pagoRepository.findById(turno.pago().getId()).orElseThrow().setMedioPago(null));
        expirarTurno(c);
        var p = pagos.procesarPago(c.getId(), "historico-pendiente", null);
        assertNull(p.getMedioPago()); assertEquals(EstadoCompra.COMPLETADA, consultar(c).getEstado());
        assertEquals(1, pagoRepository.count()); verify(pasarela, times(1)).procesarPago(any(), any());
    }
}
