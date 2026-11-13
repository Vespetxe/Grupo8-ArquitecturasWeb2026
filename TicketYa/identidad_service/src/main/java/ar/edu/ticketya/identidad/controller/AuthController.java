package ar.edu.ticketya.identidad.controller;

import ar.edu.ticketya.identidad.dto.AuthDtos.*;
import ar.edu.ticketya.identidad.service.IdentityService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;

@RestController @RequestMapping("/auth")
public class AuthController {
    private final IdentityService identity;
    public AuthController(IdentityService identity){this.identity=identity;}
    @PostMapping("/register") ResponseEntity<UserView> register(@Valid @RequestBody Credentials input){
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(identity.register(input));
    }
    @PostMapping("/login") ResponseEntity<TokenResponse> login(@Valid @RequestBody Credentials input){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(identity.login(input));
    }
    @GetMapping("/me") ResponseEntity<UserView> me(@AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identity.me(jwt.getSubject()));
    }
}
