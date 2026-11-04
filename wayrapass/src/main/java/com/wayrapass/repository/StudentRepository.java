package com.wayrapass.repository;

import com.wayrapass.model.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @Override
    @EntityGraph(attributePaths = {"user", "institution"})
    List<Student> findAll();
    @EntityGraph(attributePaths = "institution")
    Optional<Student> findByUserId(Long userId);
    @EntityGraph(attributePaths = {"user", "institution"})
    Optional<Student> findByFamilyLinkCodeHash(String familyLinkCodeHash);
    boolean existsByFamilyLinkCodeHash(String familyLinkCodeHash);
    boolean existsByInstitutionIdAndStudentCodeIgnoreCase(Long institutionId, String studentCode);
}
