package grupo22.eventocatalogo_service.service;
import grupo22.eventocatalogo_service.observability.Audit;
import grupo22.eventocatalogo_service.observability.AuditTx;

import grupo22.eventocatalogo_service.DTO.response.EventResponseDTO;
import grupo22.eventocatalogo_service.domain.*;
import grupo22.eventocatalogo_service.repository.*;
import grupo22.eventocatalogo_service.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import grupo22.eventocatalogo_service.resilience.PilotReadGuard;

@Service
public class EventWorkflow {
    private final EventService events;
    private final EventRepository repository;
    private final SectorRepository sectors;
    private final TransactionTemplate tx;
    private final RestClient inventory,compras,entradas;
    private final PilotReadGuard initialization,lifecycle,emissionBlock,cancellation;
    record SeatSnapshot(Long catalogoAsientoId,String fila,Integer numero) {}
    record StockSnapshot(Long idSector,boolean esNumerado,int capacidadTotal,List<SeatSnapshot> asientos) {}
    @org.springframework.beans.factory.annotation.Autowired
    public EventWorkflow(EventService events,EventRepository repository,SectorRepository sectors,PlatformTransactionManager manager,
            @Value("${ticketya.inventario.base-url:http://localhost:8082}") String inventoryUrl,
            @Value("${ticketya.compras.base-url:http://localhost:8084}") String comprasUrl,
            @Value("${ticketya.entradas.base-url:http://localhost:8083}") String entradasUrl, grupo22.eventocatalogo_service.security.ServiceTokens tokens,org.springframework.core.env.Environment env) {
        this.events=events;this.repository=repository;this.sectors=sectors;tx=new TransactionTemplate(manager);
        inventory=tokens.client("inventario",inventoryUrl);
        compras=tokens.client("compras",comprasUrl);
        entradas=tokens.client("entradas",entradasUrl);
        initialization=new PilotReadGuard(env,PilotReadGuard.Family.INVENTORY_INIT);
        lifecycle=new PilotReadGuard(env,PilotReadGuard.Family.INVENTORY_LIFECYCLE);
        emissionBlock=new PilotReadGuard(env,PilotReadGuard.Family.EMISSION_BLOCK);
        cancellation=new PilotReadGuard(env,PilotReadGuard.Family.CANCELLATION);
    }
    public EventWorkflow(EventService events,EventRepository repository,SectorRepository sectors,PlatformTransactionManager manager,String inventoryUrl,String comprasUrl,String entradasUrl,grupo22.eventocatalogo_service.security.ServiceTokens tokens){this(events,repository,sectors,manager,inventoryUrl,comprasUrl,entradasUrl,tokens,new org.springframework.core.env.StandardEnvironment());}
    public EventResponseDTO publish(Long id) {
        var event=events.getEventById(id);
        if(event.getStatus()==EventStatus.PUBLICADO){habilitarPublicacion(id);var result=events.getEventById(id);
            AuditTx.completed(Audit.capture(Audit.Action.EVENT_PUBLICATION_REPLAY,Audit.Resource.EVENT,id,Audit.operation(Audit.Resource.EVENT,id),Audit.Outcome.SUCCESS,result.getStatus()));return result;}
        if(event.getStatus()!=EventStatus.BORRADOR)throw new BusinessRuleException("Solo se publica un borrador");
        List<StockSnapshot> stock=tx.execute(status->{
            var e=repository.findById(id).orElseThrow();
            var list=sectors.findByVenueVenueId(e.getVenueId());
            if(list.isEmpty())throw new BusinessRuleException("El lugar requiere sectores");
            var prices=e.getSectorPrices().stream().map(EventSectorPrice::getSectorId).toList();
            if(list.stream().anyMatch(s->!prices.contains(s.getSectorId())))throw new BusinessRuleException("Faltan precios");
            return list.stream().map(s->new StockSnapshot(s.getSectorId(),s.getType()==SectorType.NUMERADO,s.getCapacity(),
                    s.getSeats().stream().map(a->new SeatSnapshot(a.getSeatId(),a.getRowLabel(),a.getSeatNumber())).toList())).toList();
        });
        try {
            for(var sector:stock){
                initialization.execute(()->{
                var acknowledgement=inventory.post().uri("/eventos/{id}/inventario",id).body(sector).retrieve().body(tools.jackson.databind.JsonNode.class);
                if(acknowledgement==null || !acknowledgement.path("exitoso").isBoolean() || !acknowledgement.path("exitoso").asBoolean())
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Inicializacion de inventario sin confirmacion; reintente");
                return acknowledgement;
                });
            }
        } catch(org.springframework.web.client.RestClientResponseException ex) {
            if(ex.getStatusCode().value()==401||ex.getStatusCode().value()==403)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Autenticacion tecnica pendiente; reintente sin cambiar la operacion");
            if(inventoryRejection(ex,true))throw new BusinessRuleException("Inicializacion de inventario incompatible o bloqueada; no reintentar sin revisar la configuracion");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo inicializar el inventario; reintente",ex);
        } catch(org.springframework.web.client.RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo inicializar el inventario; reintente",ex);
        }
        try {events.publishEvent(id);}
        catch(org.springframework.dao.OptimisticLockingFailureException | BusinessRuleException race){
            if(events.getEventById(id).getStatus()!=EventStatus.PUBLICADO)throw race;
        }
        habilitarPublicacion(id);
        var result=events.getEventById(id);
        AuditTx.completed(Audit.capture(Audit.Action.EVENT_PUBLICATION,Audit.Resource.EVENT,id,Audit.operation(Audit.Resource.EVENT,id),Audit.Outcome.SUCCESS,result.getStatus()));
        return result;
    }
    private void habilitarPublicacion(Long id) {
        try {lifecycle.execute(()->inventory.post().uri("/internal/eventos/{id}/publicar",id).retrieve().toBodilessEntity());}
        catch(org.springframework.web.client.RestClientResponseException ex){
            if(ex.getStatusCode().value()==401||ex.getStatusCode().value()==403)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Autenticacion tecnica pendiente; reintente sin cambiar la operacion");
            if(inventoryRejection(ex,false))throw new BusinessRuleException("Publicacion bloqueada por un estado de inventario incompatible");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Evento publicado con habilitacion pendiente; reintente publicar",ex);
        }catch(org.springframework.web.client.RestClientException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Evento publicado con habilitacion pendiente; reintente publicar",ex);}
    }
    public EventResponseDTO cancel(Long id,String role,Long administradorId) {
        if(administradorId==null || administradorId<=0)throw new BusinessRuleException("Se requiere X-Admin-Id positivo");
        var event=events.getEventById(id);
        if(!"ADMIN".equalsIgnoreCase(role))throw new BusinessRuleException("Se requiere identificar administrador");
        if(event.getStatus()==EventStatus.FINALIZADO)throw new BusinessRuleException("Evento finalizado");
        if(!grupo22.eventocatalogo_service.security.CurrentActor.admin())throw new org.springframework.security.access.AccessDeniedException("Administrator required");
        var originalAdmin=new java.util.concurrent.atomic.AtomicReference<Long>();
        var retry=new java.util.concurrent.atomic.AtomicBoolean();
        tx.executeWithoutResult(status->{
            var locked=repository.lockForCancellation(id).orElseThrow(grupo22.eventocatalogo_service.security.CurrentActor::missing);
            retry.set(locked.getCancellationAuthorizedAdminId()!=null);
            locked.authorizeCancellation(grupo22.eventocatalogo_service.security.CurrentActor.humanId(),grupo22.eventocatalogo_service.security.CurrentActor.jwt().getId());
            repository.save(locked);
            originalAdmin.set(locked.getCancellationAuthorizedAdminId());
            if(!retry.get())AuditTx.local(Audit.capture(Audit.Action.EVENT_CANCELLATION_AUTHORIZED,Audit.Resource.EVENT,locked.getEventId(),Audit.operation(Audit.Resource.EVENT,locked.getEventId()),Audit.Outcome.PENDING,null).initiated(originalAdmin.get()));
        });
        try {
        try {emissionBlock.execute(()->entradas.post().uri("/internal/events/{id}/cancel-emission",id).retrieve().toBodilessEntity());}
        catch(org.springframework.web.client.RestClientException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo bloquear la emision; reintente",ex);}
        if(event.getStatus()!=EventStatus.CANCELADO) {
            try {event=events.cancelEvent(id,role);}
            catch(org.springframework.dao.OptimisticLockingFailureException | BusinessRuleException race){
                event=events.getEventById(id);if(event.getStatus()!=EventStatus.CANCELADO)throw race;
            }
        }
        try {
            lifecycle.execute(()->inventory.post().uri("/eventos/{id}/cancelar-inventario",id).retrieve().toBodilessEntity());
            var result=cancellation.execute(()->{
            var response=compras.post().uri("/admin/eventos/{id}/cancelacion",id).retrieve().toEntity(tools.jackson.databind.JsonNode.class);
            var snapshot=response.getBody();
            if(!Set.of(200,202).contains(response.getStatusCode().value()) || snapshot==null || !snapshot.path("eventoId").isIntegralNumber() || snapshot.path("eventoId").asLong()!=id || !snapshot.path("devoluciones").isArray() || !snapshot.path("comprasPendientes").isArray())
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cancelacion pendiente; reintente");
            return response;
            });
            var body=result.getBody();
            if(body==null || !body.path("eventoId").isIntegralNumber() || body.path("eventoId").asLong()!=id
                || !body.path("devoluciones").isArray() || !body.path("comprasPendientes").isArray()
                || result.getStatusCode().value()!=200 || !body.path("comprasPendientes").isEmpty())
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cancelacion pendiente; reintente");
        } catch(org.springframework.web.client.RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cancelacion registrada con coordinacion pendiente; reintente",ex);
        }
        AuditTx.completed(Audit.capture(retry.get()?Audit.Action.EVENT_CANCELLATION_RETRY:Audit.Action.EVENT_CANCELLATION,Audit.Resource.EVENT,id,Audit.operation(Audit.Resource.EVENT,id),Audit.Outcome.SUCCESS,event.getStatus()).initiated(originalAdmin.get()));
        return event;
        }catch(RuntimeException failure){
            AuditTx.completed(Audit.capture(retry.get()?Audit.Action.EVENT_CANCELLATION_RETRY:Audit.Action.EVENT_CANCELLATION,Audit.Resource.EVENT,id,Audit.operation(Audit.Resource.EVENT,id),Audit.Outcome.PENDING,null).initiated(originalAdmin.get()));
            throw failure;
        }
    }
    /** Existing Inventory error DTO + exact controlled contract, never HTTP class alone. */
    private boolean inventoryRejection(org.springframework.web.client.RestClientResponseException failure,boolean initializing){
        if(failure.getResponseBodyAsByteArray().length>4096)return false;
        try{
            var body=new tools.jackson.databind.ObjectMapper().readTree(failure.getResponseBodyAsString());
            if(!body.path("exitoso").isBoolean()||body.path("exitoso").asBoolean())return false;
            String message=body.path("mensaje").asText("");int status=failure.getStatusCode().value();
            return initializing ? (status==400&&message.equals("El inventario para este evento y sector ya existe"))
                || (status==409&&message.equals("Evento suspendido o cancelado"))
                : status==409&&message.equals("Una publicacion tardia no puede reabrir un evento bloqueado");
        }catch(RuntimeException malformed){return false;}
    }
    public EventResponseDTO suspend(Long id,String role) {
        var event=events.suspendOrUpdateStatus(id,"SUSPENDIDO",role);
        try {lifecycle.execute(()->inventory.post().uri("/internal/eventos/{id}/suspender",id).retrieve().toBodilessEntity());}
        catch(org.springframework.web.client.RestClientException ex) {throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Suspension pendiente; reintente",ex);}
        return event;
    }
    public EventResponseDTO status(Long id,String status,String role){
        if(!"ADMIN".equalsIgnoreCase(role))throw new BusinessRuleException("Se requiere identificar administrador");
        if("PUBLICADO".equals(status)){
            if(events.getEventById(id).getStatus()!=EventStatus.AGOTADO)throw new BusinessRuleException("Solo se reabre por esta operacion un evento agotado");
            try{lifecycle.execute(()->inventory.post().uri("/internal/eventos/{id}/habilitar",id).retrieve().toBodilessEntity());}
            catch(org.springframework.web.client.RestClientException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo reabrir el inventario",ex);}
            return events.suspendOrUpdateStatus(id,status,role);
        }
        if(!Set.of("AGOTADO","FINALIZADO").contains(status))throw new BusinessRuleException("Operacion no permitida; usar publicar, suspender o cancelar");
        var event=events.suspendOrUpdateStatus(id,status,role);
        try{lifecycle.execute(()->inventory.post().uri("/internal/eventos/{id}/suspender",id).retrieve().toBodilessEntity());}
        catch(org.springframework.web.client.RestClientException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Estado registrado con bloqueo pendiente; reintente",ex);}
        return event;
    }
}
