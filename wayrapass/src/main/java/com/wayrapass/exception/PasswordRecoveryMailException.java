package com.wayrapass.exception;

public class PasswordRecoveryMailException extends RuntimeException {

    public PasswordRecoveryMailException(Throwable cause) {
        super("No se pudo enviar el correo de recuperación. Intenta nuevamente.", cause);
    }
}
