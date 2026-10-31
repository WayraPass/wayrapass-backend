package com.wayrapass.service;

import com.wayrapass.dto.request.ForgotPasswordRequest;
import com.wayrapass.dto.request.ResetPasswordRequest;
import com.wayrapass.dto.request.UserRequestDTO;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.PasswordRecoveryMailException;
import com.wayrapass.mapper.UserMapper;
import com.wayrapass.model.PasswordResetToken;
import com.wayrapass.model.Role;
import com.wayrapass.model.User;
import com.wayrapass.repository.InstitutionRepository;
import com.wayrapass.repository.PasswordResetTokenRepository;
import com.wayrapass.repository.StudentRepository;
import com.wayrapass.repository.UserRepository;
import com.wayrapass.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock StudentRepository students;
    @Mock InstitutionRepository institutions;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock PasswordEncoder passwords;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwt;
    @Mock UserMapper mapper;
    @Mock PasswordRecoveryMailService recoveryMail;

    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = service(false);
        user = new User();
        user.setId(10L);
        user.setFullName("Ana Torres");
        user.setEmail("ana@example.com");
        user.setPasswordHash("old-password-hash");
        user.setPhone("999888777");
        user.setRole(Role.STUDENT);
        user.setEnabled(true);
    }

    @Test
    void publicRegistrationRejectsPrivilegedRoles() {
        assertThrows(BusinessRuleException.class, () -> service.register(new UserRequestDTO(
                "Coordinator", "coordinator@example.com", "very-secure-password",
                null, Role.COORDINATOR, null, null)));
        assertThrows(BusinessRuleException.class, () -> service.register(new UserRequestDTO(
                "Driver", "driver@example.com", "very-secure-password",
                null, Role.DRIVER, null, null)));
    }

    @Test
    void rolesAreExactlyTheDomainRoles() {
        assertArrayEquals(
                new Role[]{Role.STUDENT, Role.FAMILY, Role.DRIVER, Role.COORDINATOR},
                Role.values());
    }

    @Test
    void forgotPasswordStoresOnlyHashAndInvokesMailWithRawToken() {
        when(users.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));

        var response = service.forgotPassword(new ForgotPasswordRequest(" ANA@example.com "));

        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> rawToken = ArgumentCaptor.forClass(String.class);
        verify(resetTokens).saveAndFlush(stored.capture());
        verify(recoveryMail).sendPasswordReset(eq(user), rawToken.capture());
        assertEquals(43, rawToken.getValue().length());
        assertEquals(sha256(rawToken.getValue()), stored.getValue().getTokenHash());
        assertNotEquals(rawToken.getValue(), stored.getValue().getTokenHash());
        assertTrue(stored.getValue().getExpiresAt().isAfter(Instant.now().plusSeconds(890)));
        assertNull(response.developmentToken());
        assertFalse(response.message().contains(user.getEmail()));
    }

    @Test
    void forgotPasswordForUnknownAddressPreservesEnumerationSafeContract() {
        when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        var response = service.forgotPassword(new ForgotPasswordRequest("unknown@example.com"));

        assertEquals("Si el correo existe, se generó un enlace de recuperación.", response.message());
        assertNull(response.developmentToken());
        verifyNoInteractions(resetTokens, recoveryMail);
    }

    @Test
    void mailFailureRemovesGeneratedTokenAndRaisesControlledException() {
        when(users.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));
        doThrow(new PasswordRecoveryMailException(new IllegalStateException("SMTP unavailable")))
                .when(recoveryMail).sendPasswordReset(eq(user), anyString());

        assertThrows(PasswordRecoveryMailException.class,
                () -> service.forgotPassword(new ForgotPasswordRequest("ana@example.com")));

        ArgumentCaptor<PasswordResetToken> token = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).saveAndFlush(token.capture());
        verify(resetTokens).delete(token.getValue());
        verify(resetTokens).flush();
    }

    @Test
    void validResetChangesOnlyPasswordAndConsumesToken() {
        PasswordResetToken token = token(Instant.now().plusSeconds(300), null);
        when(resetTokens.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(token));
        when(passwords.encode("new-secure-password")).thenReturn("new-password-hash");

        service.resetPassword(new ResetPasswordRequest("raw-token", "new-secure-password"));

        assertEquals("new-password-hash", user.getPasswordHash());
        assertNotNull(token.getUsedAt());
        assertEquals(Role.STUDENT, user.getRole());
        assertEquals("ana@example.com", user.getEmail());
        assertEquals("999888777", user.getPhone());
        verify(resetTokens).save(token);
        verify(users).save(user);
    }

    @Test
    void expiredResetTokenIsRejectedWithoutChangingUser() {
        PasswordResetToken token = token(Instant.now().minusSeconds(1), null);
        when(resetTokens.findByTokenHash(sha256("expired-token"))).thenReturn(Optional.of(token));

        assertThrows(BusinessRuleException.class,
                () -> service.resetPassword(new ResetPasswordRequest("expired-token", "new-secure-password")));

        assertEquals("old-password-hash", user.getPasswordHash());
        verifyNoInteractions(passwords);
        verify(resetTokens, never()).save(any());
    }

    @Test
    void reusedResetTokenIsRejectedWithoutChangingUser() {
        PasswordResetToken token = token(Instant.now().plusSeconds(300), Instant.now().minusSeconds(1));
        when(resetTokens.findByTokenHash(sha256("used-token"))).thenReturn(Optional.of(token));

        assertThrows(BusinessRuleException.class,
                () -> service.resetPassword(new ResetPasswordRequest("used-token", "new-secure-password")));

        assertEquals("old-password-hash", user.getPasswordHash());
        verifyNoInteractions(passwords);
    }

    private AuthService service(boolean exposeResetToken) {
        return new AuthService(
                users, students, institutions, resetTokens, passwords,
                authenticationManager, jwt, mapper, recoveryMail, exposeResetToken);
    }

    private PasswordResetToken token(Instant expiresAt, Instant usedAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("stored-hash");
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        return token;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
