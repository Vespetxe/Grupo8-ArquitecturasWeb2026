
package ar.edu.unicen.grupo8.comprasservice.repository;

import ar.edu.unicen.grupo8.comprasservice.entity.enums.EstadoCompra;
import java.util.Collection;
import ar.edu.unicen.grupo8.comprasservice.entity.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    @Query("""
      select distinct c.eventoId from Compra c
      where c.identityOwnerId=:owner and c.estado in :states and c.cancelacionSolicitada=false and c.importeTotal>0
      and exists (select p.id from Pago p where p.compra=c and p.estado in :payments and p.importe>coalesce(p.importeReintegrado,0))
      order by c.eventoId
      """)
    List<Long> recommendationEvents(@Param("owner") Long owner,@Param("states") Collection<EstadoCompra> states,
       @Param("payments") Collection<ar.edu.unicen.grupo8.comprasservice.entity.enums.EstadoPago> payments);

    List<Compra> findByClienteId(Long clienteId);

    List<Compra> findByEventoId(Long eventoId);

    List<Compra> findByClienteIdAndEventoIdAndEstado(Long clienteId, Long eventoId, EstadoCompra estado);

    Optional<Compra> findByReservaId(Long reservaId);



    @Query("""
    SELECT COALESCE(SUM(d.cantidad), 0)
    FROM Compra c
    JOIN c.detalles d
    WHERE c.clienteId = :clienteId
      AND c.eventoId = :eventoId
      AND c.estado IN :estados
    """)
    Long contarEntradasPorClienteYEvento(
            @Param("clienteId") Long clienteId,
            @Param("eventoId") Long eventoId,
            @Param("estados") Collection<EstadoCompra> estados
    );


    @Query("""
        SELECT COUNT(DISTINCT dd.entradaId) FROM DetalleDevolucion dd
        WHERE dd.devolucion.compra.clienteId = :clienteId
          AND dd.devolucion.compra.eventoId = :eventoId
          AND dd.devolucion.compra.estado IN :estados
          AND EXISTS (SELECT r.id FROM Reintegro r WHERE r.devolucion = dd.devolucion AND r.estado = :estadoReintegro)
        """)
    long contarEntradasReintegradas(@Param("clienteId") Long clienteId, @Param("eventoId") Long eventoId,
            @Param("estados") Collection<EstadoCompra> estados,
            @Param("estadoReintegro") ar.edu.unicen.grupo8.comprasservice.entity.enums.EstadoReintegro estadoReintegro);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compra c WHERE c.id = :compraId")
    Optional<Compra> buscarPorIdConBloqueo(
            @Param("compraId") Long compraId
    );



}
