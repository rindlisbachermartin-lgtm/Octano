package com.octano.backend.turnos.exception;

import com.octano.backend.turnos.dto.TurnosDatos.Conflicto;
import java.util.List;

public class SuperposicionTurnoException extends RuntimeException {
    private final List<Conflicto> conflictos;
    private final boolean puedeConfirmar;
    public SuperposicionTurnoException(List<Conflicto> conflictos, boolean puedeConfirmar) {
        super("El horario se superpone con otros turnos. Un administrador debe confirmar el turno simultáneo.");
        this.conflictos = List.copyOf(conflictos);
        this.puedeConfirmar = puedeConfirmar;
    }
    public List<Conflicto> getConflictos() { return conflictos; }
    public boolean isPuedeConfirmar() { return puedeConfirmar; }
}
