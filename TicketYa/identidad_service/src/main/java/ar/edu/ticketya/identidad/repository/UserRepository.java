package ar.edu.ticketya.identidad.repository;
import ar.edu.ticketya.identidad.entity.IdentityUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<IdentityUser,Long> {
    Optional<IdentityUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
