package ar.edu.ticketya.identidad.observability;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class HttpLogConfiguration {
    @Bean FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter(io.micrometer.tracing.Tracer tracer) {
        var registration = new FilterRegistrationBean<>(new HttpLoggingFilter(tracer));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }
}
