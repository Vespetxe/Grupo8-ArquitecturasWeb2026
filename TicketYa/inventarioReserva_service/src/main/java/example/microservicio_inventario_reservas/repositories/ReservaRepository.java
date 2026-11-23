package example.microservicio_inventario_reservas.repositories;

import example.microservicio_inventario_reservas.entities.Reserva;
import example.microservicio_inventario_reservas.enums.EstadoReserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository("ReservaRepository")
public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.cantidad),0) from Reserva r where r.idCliente=:cliente and r.idEvento=:evento and (r.estado=example.microservicio_inventario_reservas.enums.EstadoReserva.CONFIRMADA or (r.estado=example.microservicio_inventario_reservas.enums.EstadoReserva.PENDIENTE and r.fechaExpiracion>:ahora))")
    long cupoOcupado(@org.springframework.data.repository.query.Param("cliente") Long cliente,
        @org.springframework.data.repository.query.Param("evento") Long evento,
        @org.springframework.data.repository.query.Param("ahora") LocalDateTime ahora);
    @org.springframework.data.jpa.repository.Query("select count(d) from LugarDevuelto d where d.reserva.idCliente=:cliente and d.reserva.idEvento=:evento and d.reserva.estado=example.microservicio_inventario_reservas.enums.EstadoReserva.CONFIRMADA")
    long cupoDevuelto(@org.springframework.data.repository.query.Param("cliente") Long cliente,
        @org.springframework.data.repository.query.Param("evento") Long evento);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Reserva r where r.idReserva = :id")
    java.util.Optional<Reserva> buscarConBloqueo(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select r.idReserva from Reserva r where r.estado = example.microservicio_inventario_reservas.enums.EstadoReserva.PENDIENTE and r.fechaExpiracion < :ahora")
    List<Long> buscarIdsVencidos(@org.springframework.data.repository.query.Param("ahora") LocalDateTime ahora);

    boolean existsByIdEvento(Long idEvento);

    List<Reserva> findByIdCliente(Long idCliente);

    List<Reserva> findByEstadoAndFechaExpiracionBefore(EstadoReserva estado, LocalDateTime fechaLimite);

    List<Reserva> findByIdEventoAndEstado(Long idEvento, EstadoReserva estado);
}
