package ar.edu.unicen.grupo8.comprasservice.observability;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Exercises Boot's native encoder against the same configuration imported in production. */
public final class EcsLogFixture implements AutoCloseable {
    public final LoggerContext context = new LoggerContext();
    public final StructuredLogEncoder encoder = new StructuredLogEncoder();

    public EcsLogFixture() throws IOException {
        var environment = new MockEnvironment();
        var properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("logging.properties"));
        properties.forEach((key, value) -> environment.setProperty(key.toString(), value.toString()));
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putObject(Environment.class.getName(), environment);
        encoder.setContext(context);
        encoder.setFormat(environment.getRequiredProperty("logging.structured.format.console"));
        encoder.start();
    }

    public String encode(ILoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    @Override public void close() { encoder.stop(); context.stop(); }
}
