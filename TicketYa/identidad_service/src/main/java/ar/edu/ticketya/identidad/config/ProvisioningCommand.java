package ar.edu.ticketya.identidad.config;

import ar.edu.ticketya.identidad.service.ProvisioningService;
import ar.edu.ticketya.identidad.entity.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.nio.file.*;
import java.util.*;

@Configuration @Profile("provision")
public class ProvisioningCommand {
    @Bean CommandLineRunner provision(ProvisioningService service,Environment env,org.springframework.context.ApplicationContext context){return args->{
        if(context instanceof org.springframework.web.context.WebApplicationContext)
            throw new IllegalStateException("Provisioning requires CLI-only mode");
        String location=env.getRequiredProperty("IDENTITY_PROVISION_FILE");
        Properties p=new Properties();
        try(var input=Files.newBufferedReader(Path.of(location))) {p.load(input);}
        // Never echo input, exceptions, passwords, tokens or configuration to console.
        try {
            switch(p.getProperty("operation","")) {
                case "first-admin" -> service.firstAdmin(p.getProperty("email",""),p.getProperty("password",""));
                case "user" -> service.privilegedUser(p.getProperty("email",""),p.getProperty("password",""),Role.valueOf(p.getProperty("role","")));
                case "client" -> service.technicalClient(p.getProperty("caller",""),p.getProperty("audience",""),
                    new HashSet<>(Arrays.asList(p.getProperty("scopes","").split(" "))),p.getProperty("secret",""));
                default -> throw new IllegalArgumentException();
            }
        } catch(RuntimeException ex){throw new IllegalStateException("Provisioning failed; no supplied credentials are included in diagnostics");}
        finally {p.clear();}
        System.out.println("Identity provisioning completed");
    };}
}
