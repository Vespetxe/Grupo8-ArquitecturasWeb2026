package ar.edu.ticketya.identidad.security;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class TechnicalClientPolicyTest {
 @Test void readPermissionIsLimitedToExistingEventsPurchasesLink(){assertTrue(TechnicalClientPolicy.allows("eventos","ticketya-compras",Set.of("purchases.history.read")));assertFalse(TechnicalClientPolicy.allows("entradas","ticketya-compras",Set.of("purchases.history.read")));}
 @Test void historyDoesNotGrantFinancialOrAdministrativeScopes(){assertFalse(TechnicalClientPolicy.allows("eventos","ticketya-compras",Set.of("orders.read")));assertTrue(TechnicalClientPolicy.allows("eventos","ticketya-compras",Set.of("purchases.event-cancel","purchases.history.read")));}
 @Test void notificationPublicationIsLimitedToEventsPurchases(){assertTrue(TechnicalClientPolicy.allows("eventos","ticketya-compras",Set.of("notifications.publish")));assertFalse(TechnicalClientPolicy.allows("entradas","ticketya-compras",Set.of("notifications.publish")));assertFalse(TechnicalClientPolicy.allows("compras","ticketya-eventos",Set.of("notifications.publish")));}
 @Test void reportScopesStayOnExistingEventsLinks(){for(String target:new String[]{"compras","inventario","entradas"}){String scope=target.equals("compras")?"reports.sales.read":target.equals("inventario")?"reports.inventory.read":"reports.tickets.read";assertTrue(TechnicalClientPolicy.allows("eventos","ticketya-"+target,Set.of(scope)));assertFalse(TechnicalClientPolicy.allows("entradas","ticketya-"+target,Set.of(scope)));}}
}
