package grupo22.eventocatalogo_service.notifications;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="event_notification_outbox")
public class EventNotice {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(name="event_id",nullable=false) Long eventId;
 @Column(nullable=false,length=40) String type;
 @Column(nullable=false) Instant createdAt;
 Instant deliveredAt;
 Instant lastAttemptAt;
 @Column(length=150) String city;
 @Column(columnDefinition="TEXT") String participants;
 protected EventNotice(){}
}
