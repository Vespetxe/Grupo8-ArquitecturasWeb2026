package example.microservicio_inventario_reservas.dtos;

import example.microservicio_inventario_reservas.enums.EstadoReserva;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor

@ToString
public class ReservaResponse {
    private Long identityOwnerId;
    public ReservaResponse(Long id,Long cliente,Long evento,Long sector,Integer cantidad,LocalDateTime creacion,LocalDateTime expiracion,EstadoReserva estado,List<DetalleAsientoResponse> asientos){
        this.idReserva=id;this.idCliente=cliente;this.idEvento=evento;this.idSector=sector;this.cantidad=cantidad;this.fechaCreacion=creacion;this.fechaExpiracion=expiracion;this.estado=estado;this.asientosReservados=asientos;
    }

    private Long idReserva;
    private Long idCliente;
    private Long idEvento;
    private Long idSector;
    private Integer cantidad;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaExpiracion;
    private EstadoReserva estado;
    private List<DetalleAsientoResponse> asientosReservados; // Lista asientos asignados a la reserva
}
