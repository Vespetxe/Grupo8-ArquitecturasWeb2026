package ar.edu.ticketya.gateway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args){SpringApplication.run(GatewayApplication.class,args);}
    @Bean RouteLocator routes(RouteLocatorBuilder b,Environment e){
        return b.routes()
            .route("eventos",r->r.path("/api/v1/events/**","/api/v1/venues/**","/api/v1/sectors/**","/api/v1/participants/**").uri(e.getRequiredProperty("ticketya.eventos.url")))
            .route("reservas",r->r.path("/reservas","/reservas/{id}","/disponibilidad/**").uri(e.getRequiredProperty("ticketya.inventario.url")))
            .route("entradas",r->r.path("/tickets").and().method("GET").uri(e.getRequiredProperty("ticketya.entradas.url")))
            .route("compras",r->r.path("/compras/**","/pagos/**","/devoluciones/**","/admin/devoluciones","/admin/devoluciones/**","/admin/reintegros/{id}").uri(e.getRequiredProperty("ticketya.compras.url")))
            .route("identidad",r->r.path("/auth/register","/auth/login","/auth/me").uri(e.getRequiredProperty("ticketya.identidad.url")))
            .build();
    }
    @Bean @org.springframework.core.annotation.Order(-2) org.springframework.web.server.WebExceptionHandler upstreamErrors(){
        return (exchange,error)->{
            if(error instanceof org.springframework.web.server.ResponseStatusException || error instanceof org.springframework.security.core.AuthenticationException || error instanceof org.springframework.security.access.AccessDeniedException)return reactor.core.publisher.Mono.error(error);
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            byte[] body="{\"status\":503,\"message\":\"Servicio no disponible\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        };
    }
}
