package grupo22.eventocatalogo_service.notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EventNoticeRepository extends JpaRepository<EventNotice,Long>{List<EventNotice> findTop20ByDeliveredAtIsNullOrderByIdAsc();}
