package com.wayrapass.exception;

public class WayriaUnavailableException extends RuntimeException {
    public WayriaUnavailableException() {
        super("No pude consultar la información del servicio en este momento. Intenta nuevamente más tarde.");
    }

    public WayriaUnavailableException(Throwable cause) {
        super("No pude consultar la información del servicio en este momento. Intenta nuevamente más tarde.", cause);
    }
}
