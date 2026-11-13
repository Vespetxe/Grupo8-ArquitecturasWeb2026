package ar.edu.ticketya.identidad.repository;
import ar.edu.ticketya.identidad.entity.TechnicalClient;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TechnicalClientRepository extends JpaRepository<TechnicalClient,String> {}
