package com.octano.backend.turnos.controller;

import com.octano.backend.turnos.exception.SuperposicionTurnoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TurnoController.class)
public class TurnoExceptionHandler {
    @ExceptionHandler(SuperposicionTurnoException.class)
    ProblemDetail superposicion(SuperposicionTurnoException exception) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problema.setProperty("codigo", "SUPERPOSICION_TURNO");
        problema.setProperty("requiereConfirmacion", true);
        problema.setProperty("puedeConfirmar", exception.isPuedeConfirmar());
        problema.setProperty("conflictos", exception.getConflictos());
        return problema;
    }
}
