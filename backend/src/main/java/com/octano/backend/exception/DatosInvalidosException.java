package com.octano.backend.exception;

public class DatosInvalidosException extends RuntimeException {
    public DatosInvalidosException(String mensaje) { super(mensaje); }
}
