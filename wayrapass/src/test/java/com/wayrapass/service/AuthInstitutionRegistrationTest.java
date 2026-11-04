package com.wayrapass.service;

import com.wayrapass.dto.request.UserRequestDTO;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.mapper.UserMapper;
import com.wayrapass.model.Institution;
import com.wayrapass.model.Role;
import com.wayrapass.model.Student;
import com.wayrapass.repository.*;
import com.wayrapass.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthInstitutionRegistrationTest {
    @Mock UserRepository users;
    @Mock StudentRepository students;
    @Mock InstitutionRepository institutions;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock PasswordEncoder passwords;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwt;
    @Mock UserMapper mapper;
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, students, institutions, resetTokens, passwords,
                authenticationManager, jwt, mapper);
        when(passwords.encode(anyString())).thenReturn("encoded");
    }

    @Test
    void studentRegistrationAssociatesSelectedActiveInstitution() {
        Institution institution = institution(25L, true);
        when(institutions.findById(25L)).thenReturn(Optional.of(institution));

        service.register(request(25L));

        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(students).save(saved.capture());
        assertSame(institution, saved.getValue().getInstitution());
    }

    @Test
    void nonexistentInstitutionIsRejected() {
        when(institutions.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.register(request(999L)));
        verify(students, never()).save(any());
    }

    @Test
    void inactiveInstitutionIsRejected() {
        when(institutions.findById(25L)).thenReturn(Optional.of(institution(25L, false)));
        assertThrows(BusinessRuleException.class, () -> service.register(request(25L)));
        verify(students, never()).save(any());
    }

    private static UserRequestDTO request(Long institutionId) {
        return new UserRequestDTO("Ana Torres", "ana@example.com", "VerySecure123!",
                null, Role.STUDENT, institutionId, "20260001");
    }

    private static Institution institution(Long id, boolean active) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName("UPC");
        institution.setCampusName("Monterrico");
        institution.setActive(active);
        return institution;
    }
}
