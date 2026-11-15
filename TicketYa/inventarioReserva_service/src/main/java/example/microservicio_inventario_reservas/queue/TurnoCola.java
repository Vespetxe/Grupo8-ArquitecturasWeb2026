package example.microservicio_inventario_reservas.queue;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name="turno_cola",uniqueConstraints={@UniqueConstraint(columnNames={"evento_id","cliente_id"}),@UniqueConstraint(columnNames={"evento_id","numero"})},indexes=@Index(name="idx_cola_fifo",columnList="evento_id,estado,numero"))
public class TurnoCola {
 public enum Estado { ESPERANDO, HABILITADO, USADO, EXPIRADO, ABANDONADO }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="evento_id",nullable=false) Long evento;
 @Column(name="cliente_id",nullable=false) Long cliente;
 @Column(nullable=false) long numero;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) Estado estado;
 LocalDateTime venceEn;
 Long reservaId;
 public TurnoCola(){}
}
