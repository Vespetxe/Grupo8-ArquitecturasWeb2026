package ar.edu.ticketya.identidad.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "identity_users", uniqueConstraints = @UniqueConstraint(name="uk_identity_email", columnNames="email"))
public class IdentityUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=254) private String email;
    @Column(name="password_hash", nullable=false, length=512) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private AccountStatus status=AccountStatus.ACTIVE;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="identity_user_roles", joinColumns=@JoinColumn(name="user_id"),
        uniqueConstraints=@UniqueConstraint(name="uk_identity_user_role", columnNames={"user_id","role"}))
    @Enumerated(EnumType.STRING) @Column(name="role", nullable=false, length=32)
    private Set<Role> roles = new HashSet<>();
    protected IdentityUser() {}
    public IdentityUser(String email, String hash, Set<Role> roles) { this.email=email; this.passwordHash=hash; this.roles=new HashSet<>(roles); }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountStatus getStatus() { return status; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
}
