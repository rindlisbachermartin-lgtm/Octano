package com.octano.backend.turnos.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record Turno(Long id, Long tallerId, Long clienteId, Long vehiculoId, LocalDate fecha,
                    LocalTime horaInicio, LocalTime horaFin, String motivoVisita, Estado estado,
                    boolean superposicionAutorizada, Long autorizadaPor, Instant autorizadaFecha,
                    Instant fechaModif, Long usuarioModificacionId) {
    public enum Estado { PROGRAMADO, CONFIRMADO, CANCELADO, NO_ASISTIO }
    public boolean programado() { return estado == Estado.PROGRAMADO || estado == Estado.CONFIRMADO; }
}
