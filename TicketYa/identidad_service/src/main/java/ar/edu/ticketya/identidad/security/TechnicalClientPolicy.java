package ar.edu.ticketya.identidad.security;

import java.util.Map;
import java.util.Set;

/** Single policy shared by provisioning and every subsequent database lookup. */
public final class TechnicalClientPolicy {
    private TechnicalClientPolicy() {}
    private static final Map<String,Map<String,Set<String>>> POLICY=Map.of(
        "eventos",Map.of("ticketya-inventario",Set.of("inventory.prepare","inventory.lifecycle","reports.inventory.read"),
            "ticketya-entradas",Set.of("tickets.event-block","reports.tickets.read"),"ticketya-compras",Set.of("purchases.event-cancel","purchases.history.read","notifications.publish","reports.sales.read")),
        "compras",Map.of("ticketya-eventos",Set.of("events.read"),
            "ticketya-inventario",Set.of("reservations.read","reservations.confirm","reservations.release","reservations.compensate","reservations.return"),
            "ticketya-entradas",Set.of("tickets.read","tickets.issue","tickets.annul","tickets.status")),
        "entradas",Map.of("ticketya-compras",Set.of("orders.read")));
    public static boolean allows(String caller,String audience,Set<String> scopes){
        return caller!=null && audience!=null && scopes!=null && !scopes.isEmpty()
            && POLICY.getOrDefault(caller,Map.of()).getOrDefault(audience,Set.of()).containsAll(scopes);
    }
    public static String clientId(String caller,String audience){
        return caller+"-to-"+audience.substring("ticketya-".length());
    }
    public static boolean allowsStored(String id,String audience,Set<String> scopes){
        return POLICY.keySet().stream().anyMatch(caller->allows(caller,audience,scopes) && clientId(caller,audience).equals(id));
    }
}
