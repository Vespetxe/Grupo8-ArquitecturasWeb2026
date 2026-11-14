package ar.edu.ticketya.identidad.service;
import ar.edu.ticketya.identidad.observability.Audit;
import ar.edu.ticketya.identidad.observability.AuditTx;

import ar.edu.ticketya.identidad.entity.*;
import ar.edu.ticketya.identidad.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import ar.edu.ticketya.identidad.security.TechnicalClientPolicy;

/** Operator-only application service, intentionally has no HTTP controller. */
@Service
public class ProvisioningService {
    private final UserRepository users;
    private final TechnicalClientRepository clients;
    private final ProvisioningMarkerRepository markers;
    private final PasswordEncoder passwords;
    private static final String ADMIN_MARKER="FIRST_ADMIN";
    public ProvisioningService(UserRepository users,TechnicalClientRepository clients,ProvisioningMarkerRepository markers,PasswordEncoder passwords){
        this.users=users;this.clients=clients;this.markers=markers;this.passwords=passwords;
    }
    @Transactional public Long firstAdmin(String email,String password){
        if(markers.existsById(ADMIN_MARKER) || users.findAll().stream().anyMatch(u->u.getRoles().contains(Role.ADMINISTRADOR)))
            throw new IllegalStateException("First administrator already provisioned");
        markers.saveAndFlush(new ProvisioningMarker(ADMIN_MARKER));
        return create(email,password,Role.ADMINISTRADOR);
    }
    @Transactional public Long privilegedUser(String email,String password,Role role){
        if(!markers.existsById(ADMIN_MARKER) || !Set.of(Role.ORGANIZADOR,Role.PERSONAL_ACCESO).contains(role))
            throw new IllegalStateException("Provisioning policy rejected operation");
        return create(email,password,role);
    }
    private Long create(String email,String password,Role role){
        String normalized=IdentityService.canonical(email);
        if(normalized.length()>254 || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
            || password==null || password.length()<12 || password.length()>128 || password.isBlank())
            throw new IllegalArgumentException("Invalid provisioning fields");
        if(users.existsByEmail(normalized))throw new IllegalStateException("Identity already exists");
        var saved=users.saveAndFlush(new IdentityUser(normalized,passwords.encode(password),Set.of(role)));
        AuditTx.local(Audit.capture(Audit.Action.PRIVILEGED_PROVISION,Audit.Resource.USER,saved.getId(),Audit.operation(Audit.Resource.USER,saved.getId()),Audit.Outcome.SUCCESS,role).by(Audit.system()));
        return saved.getId();
    }
    @Transactional public void technicalClient(String caller,String audience,Set<String> scopes,String secret){
        if(!markers.existsById(ADMIN_MARKER) || secret==null || secret.length()<32 || secret.length()>128 || secret.isBlank()
            || !TechnicalClientPolicy.allows(caller,audience,scopes))
            throw new IllegalArgumentException("Technical client policy rejected operation");
        String id=TechnicalClientPolicy.clientId(caller,audience);
        if(clients.existsById(id))throw new IllegalStateException("Technical client already exists");
        clients.saveAndFlush(new TechnicalClient(id,passwords.encode(secret),audience,String.join(" ",new TreeSet<>(scopes))));
        AuditTx.local(Audit.capture(Audit.Action.TECHNICAL_CLIENT_PROVISION,Audit.Resource.TECHNICAL_CLIENT,id,Audit.operation(Audit.Resource.TECHNICAL_CLIENT,id),Audit.Outcome.SUCCESS,null).by(Audit.system()));
    }
}
