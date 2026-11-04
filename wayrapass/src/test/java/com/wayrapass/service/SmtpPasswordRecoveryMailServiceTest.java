package com.wayrapass.service;

import com.wayrapass.exception.PasswordRecoveryMailException;
import com.wayrapass.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmtpPasswordRecoveryMailServiceTest {

    @Test
    void emailContainsConfiguredSenderRecipientAndRawTokenLink() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = new SmtpPasswordRecoveryMailService(
                sender,
                "security@wayrapass.test",
                "https://app.wayrapass.test"
        );
        User user = user();

        service.sendPasswordReset(user, "raw-reset-token");

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("security@wayrapass.test", message.getFrom());
        assertArrayEquals(new String[]{"ana@example.com"}, message.getTo());
        assertNotNull(message.getText());
        assertTrue(message.getText().contains(
                "https://app.wayrapass.test/reset-password?token=raw-reset-token"));
        assertFalse(message.getText().contains("password-hash"));
    }

    @Test
    void smtpFailureIsTranslatedWithoutLeakingCredentials() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("provider rejected credentials: smtp-secret"))
                .when(sender).send(any(SimpleMailMessage.class));
        var service = new SmtpPasswordRecoveryMailService(
                sender,
                "security@wayrapass.test",
                "https://app.wayrapass.test"
        );

        PasswordRecoveryMailException exception = assertThrows(
                PasswordRecoveryMailException.class,
                () -> service.sendPasswordReset(user(), "raw-reset-token"));

        assertEquals("No se pudo enviar el correo de recuperación. Intenta nuevamente.",
                exception.getMessage());
        assertFalse(exception.getMessage().contains("smtp-secret"));
        assertFalse(exception.getMessage().contains("raw-reset-token"));
    }

    private User user() {
        User user = new User();
        user.setFullName("Ana Torres");
        user.setEmail("ana@example.com");
        user.setPasswordHash("password-hash");
        return user;
    }
}
