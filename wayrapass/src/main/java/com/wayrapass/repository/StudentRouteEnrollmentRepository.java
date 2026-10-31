package com.wayrapass.repository;
import com.wayrapass.model.*; import org.springframework.data.jpa.repository.*; import jakarta.persistence.LockModeType; import java.util.*;
public interface StudentRouteEnrollmentRepository extends JpaRepository<StudentRouteEnrollment,Long>{
 Optional<StudentRouteEnrollment> findByStudentIdAndStatusIn(Long id,Collection<EnrollmentStatus> statuses);
 boolean existsByStudentIdAndRouteIdAndStatus(Long studentId,Long routeId,EnrollmentStatus status);
 boolean existsByStudentIdAndRouteIdAndStatusIn(Long studentId,Long routeId,Collection<EnrollmentStatus> statuses);
 Optional<StudentRouteEnrollment> findFirstByStudentIdOrderByIdDesc(Long studentId);
 Optional<StudentRouteEnrollment> findFirstByStudentIdAndStatusOrderByIdDesc(Long studentId,EnrollmentStatus status);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from StudentRouteEnrollment e where e.student.id=:studentId and e.id=(select max(e2.id) from StudentRouteEnrollment e2 where e2.student.id=:studentId)") Optional<StudentRouteEnrollment> findLatestForUpdate(Long studentId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from StudentRouteEnrollment e where e.student.id=:id and e.status in :statuses") Optional<StudentRouteEnrollment> findCurrentForUpdate(Long id,Collection<EnrollmentStatus> statuses);
 List<StudentRouteEnrollment> findByRouteIdAndStatus(Long routeId,EnrollmentStatus status);
 long countByRouteIdAndStatus(Long routeId,EnrollmentStatus status);
}
