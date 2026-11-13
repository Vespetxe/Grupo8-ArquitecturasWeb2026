package ar.edu.ticketya.identidad.dto;

import jakarta.validation.constraints.*;
import java.util.Set;
import ar.edu.ticketya.identidad.entity.IdentityUser;
import ar.edu.ticketya.identidad.entity.Role;

public final class AuthDtos {
    private AuthDtos() {}
    // No generated record toString(): request credentials must never appear in diagnostics.
    public static class Credentials {
        @NotBlank @Email @Size(max=254) public String email;
        @NotBlank @Size(min=12,max=128) public String password;
        public Credentials() {}
        public Credentials(String email,String password) {this.email=email;this.password=password;}
        @Override public String toString(){return "Credentials[redacted]";}
    }
    public record UserView(Long id,String email,String status,Set<Role> roles) {
        public static UserView from(IdentityUser user) {return new UserView(user.getId(),user.getEmail(),user.getStatus().name(),user.getRoles());}
    }
    public record TokenResponse(String access_token,String token_type,long expires_in) {
        @Override public String toString(){return "TokenResponse[redacted]";}
    }
}
