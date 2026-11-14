package ar.edu.ticketya.gateway.observability;
import java.util.List;
import java.util.Set;
/** Only developer-declared templates are logged. Never log a raw path or query. */
public final class HttpRoutes {
 private HttpRoutes(){}
 private static final List<String> ROUTES=List.of(
 "/api/v1/events/search","/api/v1/events/{id}/prices","/api/v1/events/{id}/publish","/api/v1/events/{id}/cancel","/api/v1/events/{id}/suspend","/api/v1/events/{id}/status","/api/v1/events/{id}","/api/v1/events",
 "/api/v1/venues/{id}/sectors","/api/v1/venues/{id}","/api/v1/venues","/api/v1/sectors/{id}/seats","/api/v1/participants/{id}","/api/v1/participants",
 "/reservas/{id}/confirmar","/reservas/{id}/liberar","/reservas/{id}","/reservas","/disponibilidad/eventos/{id}/sectores/{id}",
 "/eventos/{id}/inventario","/eventos/{id}/cancelar-inventario","/ocupaciones/liberar","/internal/eventos/{id}/publicar","/internal/eventos/{id}/habilitar","/internal/eventos/{id}/suspender","/internal/reservas/{id}/compensar","/internal/reservas/{id}/devoluciones",
 "/compras/{id}/estado","/compras/{id}/detalle","/compras/{id}/entradas","/compras/{id}/cancelar","/compras/{id}","/compras","/pagos/{id}","/pagos","/devoluciones/{id}","/devoluciones","/tickets",
 "/admin/devoluciones/{id}/aprobar","/admin/devoluciones/{id}/rechazar","/admin/devoluciones/{id}/procesar","/admin/devoluciones/{id}/procesamiento","/admin/devoluciones","/admin/reintegros/{id}","/admin/eventos/{id}/cancelacion",
 "/internal/security/readiness","/internal/orders/{id}","/internal/tickets/issue","/internal/tickets/orders/{id}/annul","/internal/tickets/status","/internal/tickets/{id}/status","/internal/events/{id}/cancel-emission",
 "/auth/register","/auth/login","/auth/me","/oauth2/token");
 public static String route(String rawPath){if(rawPath==null)return "/unmapped";for(String template:ROUTES)if(rawPath.matches(template.replace("{id}","[^/]+")))return template;return "/unmapped";}
 public static String method(String value){return value!=null&&Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS","TRACE","CONNECT").contains(value)?value:"OTHER";}
}
