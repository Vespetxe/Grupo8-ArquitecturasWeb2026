package ar.edu.ticketya.identidad.config;

import ar.edu.ticketya.identidad.service.ProvisioningService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.mockito.Mockito.*;

class ProvisioningCommandTest {
    private void run(String location) throws Exception {
        var service=mock(ProvisioningService.class);
        var tracer=mock(Tracer.class);
        var builder=mock(Span.Builder.class, RETURNS_SELF);
        var span=mock(Span.class);
        when(tracer.spanBuilder()).thenReturn(builder);
        when(builder.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(mock(Tracer.SpanInScope.class));
        new ProvisioningCommand().provision(service,
            new MockEnvironment().withProperty("IDENTITY_PROVISION_FILE",location),
            mock(ApplicationContext.class),tracer).run();
        verify(service).firstAdmin("probe@example.test","fixture-password");
        verifyNoMoreInteractions(service);
        verify(span).end();
    }
    private static final String INPUT="operation=first-admin\nemail=probe@example.test\npassword=fixture-password\n";
    @Test @ResourceLock("SYSTEM_IN")
    void stdinUsesInheritedStreamWithoutReopeningPermissionRestrictedDescriptor() throws Exception {
        var previous=System.in;
        try {System.setIn(new ByteArrayInputStream(INPUT.getBytes(StandardCharsets.UTF_8)));run("-");}
        finally {System.setIn(previous);}
    }
    @Test
    void existingPropertiesFileRemainsSupported(@TempDir Path folder) throws Exception {
        var file=folder.resolve("provision.properties");
        Files.writeString(file,INPUT,StandardCharsets.UTF_8);run(file.toString());
    }
}
