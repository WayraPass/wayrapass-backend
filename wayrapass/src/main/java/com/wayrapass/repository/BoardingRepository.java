package com.wayrapass.repository;
import com.wayrapass.model.Boarding; import org.springframework.data.jpa.repository.*; import java.time.Instant; import java.util.*;
public interface BoardingRepository extends JpaRepository<Boarding,Long>{
 boolean existsByTripIdAndStudentId(Long tripId,Long studentId);
 @Query("select b from Boarding b join fetch b.trip t join fetch t.route r join fetch r.institution join fetch b.stop left join fetch t.driver d left join fetch d.user left join fetch t.vehicle where b.student.id=:studentId and t.scheduledStartAt>=:from and t.scheduledStartAt<:toExclusive and t.scheduledStartAt<=:now order by t.scheduledStartAt desc")
 List<Boarding> findStudentHistory(Long studentId,Instant from,Instant toExclusive,Instant now);
 @Query("select b from Boarding b join fetch b.trip t join fetch t.route r join fetch r.institution join fetch b.stop left join fetch t.driver d left join fetch d.user left join fetch t.vehicle where b.student.id=:studentId and t.id=:tripId and t.scheduledStartAt<=:now")
 Optional<Boarding> findStudentHistoryDetail(Long studentId,Long tripId,Instant now);
}
