package ar.edu.unicen.grupo8.comprasservice.notifications;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
public interface NotificationRepository extends JpaRepository<Notification,String>{
 List<Notification> findByOwnerIdOrderByCreatedAtDescIdDesc(Long owner,Pageable page);
 Optional<Notification> findByIdAndOwnerId(String id,Long owner);
 long countByOwnerIdAndReadAtIsNull(Long owner);
 @Modifying @Query(value="insert into notifications (id,owner_id,type,resource_type,resource_id,created_at) values (:id,:owner,:type,:kind,:resource,:created) on duplicate key update id=id",nativeQuery=true)
 void insert(@Param("id")String id,@Param("owner")Long owner,@Param("type")String type,@Param("kind")String kind,@Param("resource")Long resource,@Param("created")Instant created);
 @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("update Notification n set n.readAt=coalesce(n.readAt,:now) where n.id=:id and n.ownerId=:owner")
 int markRead(@Param("id")String id,@Param("owner")Long owner,@Param("now")Instant now);
}
