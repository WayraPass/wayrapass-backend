package com.wayrapass.service;

import com.wayrapass.exception.PasswordRecoveryMailException;
import com.wayrapass.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SmtpPasswordRecoveryMailService implements PasswordRecoveryMailService {

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String frontendBaseUrl;

    public SmtpPasswordRecoveryMailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String mailFrom,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendPasswordReset(User user, String rawToken) {
        String resetUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .pathSegment("reset-password")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("Recuperación de contraseña de WayraPass");
        message.setText("Hola " + user.getFullName() + ",\n\n"
                + "Usa el siguiente enlace para restablecer tu contraseña. "
                + "El enlace vence en 15 minutos y solo puede utilizarse una vez:\n\n"
                + resetUrl + "\n\n"
                + "Si no solicitaste este cambio, ignora este mensaje.");

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new PasswordRecoveryMailException(exception);
        }
    }
}
