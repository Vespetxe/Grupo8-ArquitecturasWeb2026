package ar.edu.tudai.ticketya.servicio_entradas.access;
import jakarta.persistence.*;
@Entity @Table(name="event_access",uniqueConstraints=@UniqueConstraint(columnNames={"event_id","staff_id"}))
public class EventAccess {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_id",nullable=false,length=64) String eventId;
 @Column(name="staff_id",nullable=false) Long staffId;
 @Column(nullable=false) boolean active;
 public EventAccess(){}
}
