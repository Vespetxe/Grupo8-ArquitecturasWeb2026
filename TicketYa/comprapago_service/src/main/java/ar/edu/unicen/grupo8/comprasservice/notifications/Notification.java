package ar.edu.unicen.grupo8.comprasservice.notifications;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="notifications",indexes=@Index(name="ix_notification_owner_date",columnList="owner_id,created_at"))
public class Notification {
 @Id @Column(length=36) String id;
 @Column(name="owner_id",nullable=false) Long ownerId;
 @Column(nullable=false,length=40) String type;
 @Column(name="resource_type",nullable=false,length=24) String resourceType;
 @Column(name="resource_id",nullable=false) Long resourceId;
 @Column(name="created_at",nullable=false) Instant createdAt;
 @Column(name="read_at") Instant readAt;
 protected Notification(){}
 public record View(String id,String type,String resourceType,Long resourceId,Instant createdAt,boolean read,Instant readAt){}
 public View view(){return new View(id,type,resourceType,resourceId,createdAt,readAt!=null,readAt);}
}
