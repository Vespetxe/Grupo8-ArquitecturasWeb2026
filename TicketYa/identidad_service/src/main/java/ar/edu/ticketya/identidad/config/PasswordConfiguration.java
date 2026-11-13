package ar.edu.ticketya.identidad.config;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.*;
import java.util.Map;
@Configuration
public class PasswordConfiguration {
    @Bean PasswordEncoder passwordEncoder(){
        return new DelegatingPasswordEncoder("argon2id-v1",Map.of("argon2id-v1",new Argon2PasswordEncoder(16,32,1,19456,2)));
    }
}
