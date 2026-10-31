package com.wayrapass.repository;
import com.wayrapass.model.StudentFamilyLink; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface StudentFamilyLinkRepository extends JpaRepository<StudentFamilyLink,Long>{ boolean existsByStudentIdAndFamilyUserIdAndAuthorizedTrue(Long studentId,Long familyId); Optional<StudentFamilyLink> findByStudentIdAndFamilyUserId(Long studentId,Long familyId); List<StudentFamilyLink> findByFamilyUserIdAndAuthorizedTrue(Long familyId); List<StudentFamilyLink> findByStudentIdAndAuthorizedTrue(Long studentId); }
