package com.octano.backend.compartido.web;

import com.octano.backend.compartido.exception.DatosInvalidosException;
import com.octano.backend.compartido.exception.RecursoNoEncontradoException;

import org.postgresql.util.PSQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail autenticacion() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "El correo o la contraseña son incorrectos.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail permisos() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Tu rol no permite esta operación.");
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, "Revisá los campos del formulario.");
        problema.setProperty("errores", errores);
        return handleExceptionInternal(exception, problema, headers, status, request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DatosInvalidosException.class)
    ProblemDetail datosInvalidos(DatosInvalidosException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflicto(DataIntegrityViolationException exception) {
        if (exception.getMostSpecificCause() instanceof PSQLException sql && "23505".equals(sql.getSQLState()) && sql.getServerErrorMessage() != null) {
            String restriccion = sql.getServerErrorMessage().getConstraint();
            if ("vehiculo_taller_id_patente_key".equals(restriccion)) return duplicado("PATENTE_DUPLICADA", "patente", "Ya existe un vehículo con esa patente en tu taller.");
            if ("marca_nombre_unico".equals(restriccion)) return duplicado("MARCA_DUPLICADA", "nombre", "Ya existe una marca con ese nombre.");
            if ("modelo_nombre_unico".equals(restriccion)) return duplicado("MODELO_DUPLICADO", "nombre", "Ya existe un modelo con ese nombre en la marca seleccionada.");
            if ("version_nombre_unico".equals(restriccion)) return duplicado("VERSION_DUPLICADA", "nombre", "Ya existe una versión con ese nombre en el modelo seleccionado.");
        }
        // No devolvemos al cliente el SQL ni los detalles internos de PostgreSQL.
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La operación entra en conflicto con los datos guardados.");
    }
    private ProblemDetail duplicado(String codigo, String campo, String detalle) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detalle);
        problema.setProperty("codigo", codigo);
        problema.setProperty("campo", campo);
        return problema;
    }
}
