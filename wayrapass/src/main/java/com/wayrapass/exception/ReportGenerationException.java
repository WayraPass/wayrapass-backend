package com.wayrapass.exception;

public class ReportGenerationException extends RuntimeException {
    public ReportGenerationException(Throwable cause) {
        super("No se pudo generar el archivo. Intenta nuevamente", cause);
    }
}
