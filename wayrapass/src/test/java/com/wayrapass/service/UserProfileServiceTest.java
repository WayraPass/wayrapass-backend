package com.wayrapass.service;

import com.wayrapass.dto.response.UserResponse;
import com.wayrapass.model.Institution;
import com.wayrapass.model.Role;
import com.wayrapass.model.Student;
import com.wayrapass.model.User;
import com.wayrapass.repository.StudentRepository;
import com.wayrapass.repository.UserRepository;
import com.wayrapass.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {
    @Mock CurrentUser current;
    @Mock UserRepository users;
    @Mock StudentRepository students;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(current, users, students);
    }

    @Test
    void studentProfileIncludesItsRealInstitution() {
        User user = user(5L, Role.STUDENT);
        Institution institution = new Institution();
        institution.setId(25L);
        institution.setName("UPC");
        institution.setCampusName("Monterrico");
        Student student = new Student();
        student.setUser(user);
        student.setInstitution(institution);
        when(current.require()).thenReturn(user);
        when(students.findByUserId(5L)).thenReturn(Optional.of(student));

        UserResponse response = service.me();

        assertEquals(25L, response.institutionId());
        assertNotNull(response.institution());
        assertEquals(25L, response.institution().id());
        assertEquals("UPC — Monterrico", response.institution().displayName());
        verify(students).findByUserId(5L);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"FAMILY", "DRIVER", "COORDINATOR"})
    void nonStudentProfileKeepsInstitutionNull(Role role) {
        User user = user(9L, role);
        when(current.require()).thenReturn(user);

        UserResponse response = service.me();

        assertNull(response.institutionId());
        assertNull(response.institution());
        verifyNoInteractions(students);
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setFullName("Test User");
        user.setEmail("test@wayrapass.test");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }
}
