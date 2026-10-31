package com.wayrapass.service;

import com.wayrapass.dto.request.ForgotPasswordRequest;
import com.wayrapass.dto.request.LoginRequestDTO;
import com.wayrapass.dto.request.ResetPasswordRequest;
import com.wayrapass.dto.request.UserRequestDTO;
import com.wayrapass.dto.response.AuthResponseDTO;
import com.wayrapass.dto.response.PasswordResetResponse;
import com.wayrapass.exception.AccountLockedException;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.exception.PasswordRecoveryMailException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.exception.UnauthorizedException;
import com.wayrapass.mapper.UserMapper;
import com.wayrapass.model.Institution;
import com.wayrapass.model.PasswordResetToken;
import com.wayrapass.model.Role;
import com.wayrapass.model.Student;
import com.wayrapass.model.User;
import com.wayrapass.repository.InstitutionRepository;
import com.wayrapass.repository.PasswordResetTokenRepository;
import com.wayrapass.repository.StudentRepository;
import com.wayrapass.repository.UserRepository;
import com.wayrapass.security.JwtService;
import com.wayrapass.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCK_SECONDS = 300;
    private static final long RESET_TOKEN_SECONDS = 900;
    private static final String RESET_RESPONSE_MESSAGE =
            "Si el correo existe, se generó un enlace de recuperación.";

    private final UserRepository users;
    private final StudentRepository students;
    private final InstitutionRepository institutions;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwt;
    private final UserMapper mapper;
    private final PasswordRecoveryMailService recoveryMail;
    private final boolean exposeResetToken;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository users,
            StudentRepository students,
            InstitutionRepository institutions,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwords,
            AuthenticationManager authenticationManager,
            JwtService jwt,
            UserMapper mapper,
            PasswordRecoveryMailService recoveryMail,
            @Value("${app.password-reset.expose-development-token:false}") boolean exposeResetToken) {
        this.users = users;
        this.students = students;
        this.institutions = institutions;
        this.resetTokens = resetTokens;
        this.passwords = passwords;
        this.authenticationManager = authenticationManager;
        this.jwt = jwt;
        this.mapper = mapper;
        this.recoveryMail = recoveryMail;
        this.exposeResetToken = exposeResetToken;
    }

    @Transactional
    public AuthResponseDTO register(UserRequestDTO request) {
        Role role = request.role() == null ? Role.STUDENT : request.role();
        if (role != Role.STUDENT && role != Role.FAMILY) {
            throw new BusinessRuleException("El registro público solo admite STUDENT o FAMILY.");
        }
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo ya está registrado.");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setPhone(blankToNull(request.phone()));
        user.setRole(role);
        user.setEnabled(true);
        users.save(user);
        if (role == Role.STUDENT) {
            if (request.institutionId() == null) {
                throw new BusinessRuleException("institutionId es obligatorio para estudiantes.");
            }
            Institution institution = institutions.findById(request.institutionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Institución no encontrada."));
            if (request.studentCode() != null
                    && students.existsByInstitutionIdAndStudentCodeIgnoreCase(
                    institution.getId(), request.studentCode().trim())) {
                throw new ConflictException("El código estudiantil ya existe en la institución.");
            }
            Student student = new Student();
            student.setUser(user);
            student.setInstitution(institution);
            student.setStudentCode(blankToNull(request.studentCode()));
            students.save(student);
        }
        return response(user);
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class, AccountLockedException.class})
    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = users.findByEmailIgnoreCase(normalize(request.email()))
                .orElseThrow(() -> new UnauthorizedException("Correo o contraseña incorrectos."));
        Instant now = Instant.now();
        if (!user.isEnabled()) {
            throw new UnauthorizedException("La cuenta está deshabilitada.");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException("La cuenta está bloqueada temporalmente.");
        }
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), request.password()));
        } catch (AuthenticationException exception) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(now.plusSeconds(LOCK_SECONDS));
                users.save(user);
                throw new AccountLockedException("La cuenta fue bloqueada durante 5 minutos.");
            }
            users.save(user);
            throw new UnauthorizedException("Correo o contraseña incorrectos.");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        return response(user);
    }

    @Transactional
    public PasswordResetResponse forgotPassword(ForgotPasswordRequest request) {
        Optional<User> found = users.findByEmailIgnoreCase(normalize(request.email()));
        if (found.isEmpty()) {
            return resetResponse(null);
        }

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(found.get());
        resetToken.setTokenHash(hash(rawToken));
        resetToken.setExpiresAt(Instant.now().plusSeconds(RESET_TOKEN_SECONDS));
        resetTokens.saveAndFlush(resetToken);

        try {
            recoveryMail.sendPasswordReset(found.get(), rawToken);
        } catch (RuntimeException exception) {
            resetTokens.delete(resetToken);
            resetTokens.flush();
            if (exception instanceof PasswordRecoveryMailException mailException) {
                throw mailException;
            }
            throw new PasswordRecoveryMailException(exception);
        }

        return resetResponse(rawToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = resetTokens.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new BusinessRuleException("Token inválido o vencido."));
        Instant now = Instant.now();
        if (resetToken.getUsedAt() != null || !resetToken.getExpiresAt().isAfter(now)) {
            throw new BusinessRuleException("Token inválido o vencido.");
        }
        resetToken.getUser().setPasswordHash(passwords.encode(request.newPassword()));
        resetToken.setUsedAt(now);
        resetTokens.save(resetToken);
        users.save(resetToken.getUser());
    }

    private PasswordResetResponse resetResponse(String rawToken) {
        return new PasswordResetResponse(
                RESET_RESPONSE_MESSAGE,
                exposeResetToken ? rawToken : null
        );
    }

    private AuthResponseDTO response(User user) {
        return new AuthResponseDTO(jwt.generate(UserPrincipal.from(user)), "Bearer", mapper.toResponse(user));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
