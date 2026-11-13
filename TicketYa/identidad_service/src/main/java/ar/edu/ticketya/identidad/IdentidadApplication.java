package ar.edu.ticketya.identidad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.WebApplicationType;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IdentidadApplication {
    public static void main(String[] args) {
        SpringApplication app=new SpringApplication(IdentidadApplication.class);
        boolean provision=args.length==1 && "provision".equals(args[0]);
        if(provision) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles("provision");
        }
        var context=app.run(provision?new String[0]:args);
        if(provision) System.exit(SpringApplication.exit(context));
    }
}
