package grupo22.eventocatalogo_service.repository;

import grupo22.eventocatalogo_service.domain.Event;
import grupo22.eventocatalogo_service.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Event e where e.eventId = :id")
    java.util.Optional<Event> lockForCancellation(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select distinct e from Event e left join fetch e.participants where e.status=:status and e.startDateTime>:now")
    List<Event> recommendationCandidates(@org.springframework.data.repository.query.Param("status") EventStatus status,@org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="participants")
    List<Event> findByEventIdIn(java.util.Collection<Long> ids);
    List<Event> findByStatus(EventStatus status);
}