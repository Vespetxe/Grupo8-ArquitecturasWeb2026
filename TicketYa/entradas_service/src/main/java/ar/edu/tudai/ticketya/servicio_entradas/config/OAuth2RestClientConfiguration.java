package ar.edu.tudai.ticketya.servicio_entradas.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/** RestClient customization for technical OAuth2 token acquisition; business calls use Feign. */
@Configuration
public class OAuth2RestClientConfiguration {
    @Bean
    org.springframework.boot.restclient.RestClientCustomizer technicalClient() {
        return builder -> builder.requestInterceptor(new ar.edu.tudai.ticketya.servicio_entradas.observability.CorrelationInterceptor());
    }
}
