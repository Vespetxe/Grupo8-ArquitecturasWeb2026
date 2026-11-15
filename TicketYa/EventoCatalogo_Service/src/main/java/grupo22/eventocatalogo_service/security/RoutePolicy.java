package grupo22.eventocatalogo_service.security;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
public final class RoutePolicy {
 private RoutePolicy(){}
 public static java.util.List<String> requestedScopes(String caller,String target){
  return java.util.Map.of(
   "eventos:inventario",java.util.List.of("inventory.prepare","inventory.lifecycle"),
   "eventos:entradas",java.util.List.of("tickets.event-block"),
   "eventos:compras",java.util.List.of("purchases.event-cancel","purchases.history.read"),
   "compras:eventos",java.util.List.of("events.read"),
   "compras:inventario",java.util.List.of("reservations.read","reservations.confirm","reservations.release","reservations.compensate","reservations.return"),
   "compras:entradas",java.util.List.of("tickets.read","tickets.issue","tickets.annul","tickets.status"),
   "entradas:compras",java.util.List.of("orders.read")).get(caller+":"+target);
 }

 static boolean human(Authentication a,String role){return a instanceof JwtAuthenticationToken t&&"user".equals(t.getToken().getClaimAsString("token_use"))&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+role));}
 static boolean service(Authentication a,String caller,String target,String scope){return a instanceof JwtAuthenticationToken t
  &&"service".equals(t.getToken().getClaimAsString("token_use"))&&("service:"+caller+"-to-"+target).equals(t.getName())
  &&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("SCOPE_"+scope));}
 public static boolean allow(String owner,String method,String p,Authentication a){
  boolean get="GET".equals(method),post="POST".equals(method),patch="PATCH".equals(method);
  if(get&&(p.equals("/v3/api-docs")||p.startsWith("/v3/api-docs/")||p.startsWith("/swagger-ui/")))return human(a,"ADMINISTRADOR");
  if(get&&p.equals("/internal/security/readiness"))return switch(owner){
   case "eventos" -> service(a,"compras",owner,"events.read");
   case "inventario" -> service(a,"eventos",owner,"inventory.prepare")||service(a,"compras",owner,"reservations.read");
   case "entradas" -> service(a,"eventos",owner,"tickets.event-block")||service(a,"compras",owner,"tickets.read");
   case "compras" -> service(a,"eventos",owner,"purchases.event-cancel")||service(a,"entradas",owner,"orders.read");
   default -> false;
  };
  if("gateway".equals(owner)){
   if(post&&(p.equals("/auth/register")||p.equals("/auth/login")))return true;
   if(get&&p.equals("/auth/me"))return a instanceof JwtAuthenticationToken t&&"user".equals(t.getToken().getClaimAsString("token_use"));
   if(p.startsWith("/internal/")||p.matches("/admin/eventos/.*"))return false;
   if(p.startsWith("/admin/devoluciones")||p.matches("/admin/reintegros/[0-9]+"))return human(a,"ADMINISTRADOR");
   return allow("eventos",method,p,a)||allow("inventario",method,p,a)||allow("compras",method,p,a)||allow("entradas",method,p,a);
  }
  if("eventos".equals(owner)){
   // Recommendations extension: Eventos and Gateway only.
   if((get||"PUT".equals(method))&&p.equals("/api/v1/me/preferences"))return human(a,"CLIENTE");
   if(get&&p.equals("/api/v1/me/recommendations"))return human(a,"CLIENTE");
   // End recommendations extension.
   if(get&&p.equals("/api/v1/events/search"))return true;
   if(get&&(p.matches("/api/v1/venues(/[0-9]+(/sectors)?)?")||p.matches("/api/v1/participants(/[0-9]+)?")||p.matches("/api/v1/sectors/[0-9]+/seats")))return true;
   // Published detail is public; backend checks draft ownership before serialization.
   if(get&&p.matches("/api/v1/events/[0-9]+"))return human(a,"CLIENTE")||human(a,"ORGANIZADOR")||human(a,"ADMINISTRADOR")||human(a,"PERSONAL_ACCESO")||service(a,"compras","eventos","events.read");
   if((post&&(p.equals("/api/v1/venues")||p.equals("/api/v1/events")||p.matches("/api/v1/venues/[0-9]+/sectors")||p.matches("/api/v1/events/[0-9]+/prices")))||(patch&&p.matches("/api/v1/events/[0-9]+/publish")))return human(a,"ORGANIZADOR")||human(a,"ADMINISTRADOR");
   if(p.matches("/api/v1/participants(/[0-9]+)?")&&java.util.Set.of("POST","PUT","DELETE").contains(method))return human(a,"ADMINISTRADOR");
   if(patch&&p.matches("/api/v1/events/[0-9]+/(cancel|suspend|status)"))return human(a,"ADMINISTRADOR");
  }
  if("inventario".equals(owner)){
   if(get&&p.matches("/disponibilidad/eventos/[0-9]+/sectores/[0-9]+"))return true;
   if(post&&p.matches("/eventos/[0-9]+/inventario"))return service(a,"eventos",owner,"inventory.prepare");
   if(post&&(p.matches("/internal/eventos/[0-9]+/(publicar|habilitar|suspender)")||p.matches("/eventos/[0-9]+/cancelar-inventario")))return service(a,"eventos",owner,"inventory.lifecycle");
   if(post&&p.equals("/reservas"))return human(a,"CLIENTE");
   if(get&&p.equals("/reservas"))return human(a,"CLIENTE")||human(a,"ADMINISTRADOR");
   if(get&&p.matches("/reservas/[0-9]+"))return human(a,"CLIENTE")||human(a,"ADMINISTRADOR")||service(a,"compras",owner,"reservations.read");
   if(post&&p.matches("/reservas/[0-9]+/confirmar"))return service(a,"compras",owner,"reservations.confirm");
   if(post&&p.matches("/reservas/[0-9]+/liberar"))return service(a,"compras",owner,"reservations.release");
   if(post&&p.matches("/internal/reservas/[0-9]+/compensar"))return service(a,"compras",owner,"reservations.compensate");
   if((get||post)&&p.matches("/internal/reservas/[0-9]+/devoluciones"))return service(a,"compras",owner,"reservations.return");
   if(post&&p.equals("/ocupaciones/liberar"))return service(a,"compras",owner,"reservations.return");
  }
  if("entradas".equals(owner)){
   if(get&&p.equals("/tickets"))return human(a,"CLIENTE")||human(a,"ADMINISTRADOR")||service(a,"compras",owner,"tickets.read");
   if(post&&p.equals("/internal/tickets/issue"))return service(a,"compras",owner,"tickets.issue");
   if(post&&p.matches("/internal/tickets/orders/[^/]+/annul"))return service(a,"compras",owner,"tickets.annul");
   if(patch&&(p.matches("/internal/tickets/[^/]+/status")||p.equals("/internal/tickets/status")))return service(a,"compras",owner,"tickets.status");
   if(post&&p.matches("/internal/events/[^/]+/cancel-emission"))return service(a,"eventos",owner,"tickets.event-block");
  }
  if("compras".equals(owner)){
   if(post&&(p.equals("/compras")||p.equals("/pagos")||p.equals("/devoluciones")||p.matches("/compras/[0-9]+/cancelar")))return human(a,"CLIENTE");
   if(get&&(p.equals("/compras")||p.matches("/compras/[0-9]+(/(estado|detalle|entradas))?")||p.equals("/pagos")||p.matches("/(pagos|devoluciones)/[0-9]+")))return human(a,"CLIENTE")||human(a,"ADMINISTRADOR");
   if(get&&p.matches("/internal/orders/[^/]+"))return service(a,"entradas",owner,"orders.read");
   if(post&&p.matches("/admin/eventos/[0-9]+/cancelacion"))return service(a,"eventos",owner,"purchases.event-cancel");
   if((get&&p.equals("/admin/devoluciones"))||(post&&p.matches("/admin/devoluciones/[0-9]+/(aprobar|rechazar|procesar)"))
    ||(get&&p.matches("/admin/devoluciones/[0-9]+/procesamiento"))||(get&&p.matches("/admin/reintegros/[0-9]+")))return human(a,"ADMINISTRADOR");
  }
  // Deny unlisted endpoints. ResourceOwnership applies additional backend ownership checks.
  return false;
 }
}
