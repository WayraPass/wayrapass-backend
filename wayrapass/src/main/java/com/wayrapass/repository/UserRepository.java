package com.wayrapass.repository;

import com.wayrapass.model.User;
import com.wayrapass.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByRoleAndEnabledTrue(Role role);
}
