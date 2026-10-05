package com.example.exception;

// F7: excepción para datos de entrada no válidos que no se pueden comprobar con @Valid
// (p. ej. un tag nuevo sin nombre). ControllerExceptionHandler la convierte en un 400.
public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String msg) {
        super(msg);
    }

}
