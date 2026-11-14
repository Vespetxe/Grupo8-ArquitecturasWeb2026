
package ar.edu.unicen.grupo8.comprasservice.dto;

import java.time.LocalDateTime;

public record ReservaDTO(
        Long reservaId,
        Long clienteId,
        Long eventoId,
        String estado,
        LocalDateTime fechaVencimiento,
        java.util.List<DetalleEmisionDTO> detalles,
        Long identityOwnerId
) {
    public ReservaDTO(Long id,Long cliente,Long evento,String estado,LocalDateTime vencimiento,java.util.List<DetalleEmisionDTO> detalles){this(id,cliente,evento,estado,vencimiento,detalles,null);}
    public ReservaDTO(Long id, Long cliente, Long evento, String estado, LocalDateTime vencimiento) {
        this(id, cliente, evento, estado, vencimiento, java.util.List.of());
    }
}
