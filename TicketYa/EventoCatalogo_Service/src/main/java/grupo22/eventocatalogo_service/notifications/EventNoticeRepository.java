package grupo22.eventocatalogo_service.notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EventNoticeRepository extends JpaRepository<EventNotice,Long>{
 @org.springframework.data.jpa.repository.Query("select n from EventNotice n where n.deliveredAt is null order by coalesce(n.lastAttemptAt,n.createdAt),n.id")
 List<EventNotice> pending(org.springframework.data.domain.Pageable page);
 @org.springframework.data.jpa.repository.Modifying
 @org.springframework.data.jpa.repository.Query("update EventNotice n set n.lastAttemptAt=:at where n.id=:id and n.deliveredAt is null")
 int attempted(@org.springframework.data.repository.query.Param("id") Long id,@org.springframework.data.repository.query.Param("at") java.time.Instant at);
}
