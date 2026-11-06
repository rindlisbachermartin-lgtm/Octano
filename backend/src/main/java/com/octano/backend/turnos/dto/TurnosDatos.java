package com.octano.backend.turnos.dto;

import com.octano.backend.turnos.entity.Turno;
import com.octano.backend.turnos.entity.Turno.Estado;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TurnosDatos {
    private TurnosDatos() { }
    public record Solicitud(@NotNull @Positive Long clienteId, @NotNull @Positive Long vehiculoId, @Positive Long presupuestoId,
                            @NotNull LocalDate fecha, @NotNull LocalTime horaInicio, @NotNull LocalTime horaFin,
                            @NotBlank @Size(max = 500) String motivoVisita, Boolean confirmarSuperposicion) {
        public Solicitud { motivoVisita = motivoVisita == null ? null : motivoVisita.strip(); }
    }
    public record CambioEstado(@NotNull Estado estado) { }
    public record Respuesta(Long id, Long clienteId, Long vehiculoId, Long presupuestoId, LocalDate fecha, LocalTime horaInicio,
                            LocalTime horaFin, String motivoVisita, Estado estado, boolean superposicionAutorizada,
                            Long autorizadaPor, Instant autorizadaFecha, Instant fechaModif, Long usuarioModificacionId,
                            String clienteNombre, String patente, String marca, String modelo, String version, Integer anio, Integer kilometraje, Long ordenId) {
        public static Respuesta desde(Turno t) {
            return new Respuesta(t.id(), t.clienteId(), t.vehiculoId(), t.presupuestoId(), t.fecha(), t.horaInicio(), t.horaFin(), t.motivoVisita(),
                    t.estado(), t.superposicionAutorizada(), t.autorizadaPor(), t.autorizadaFecha(), t.fechaModif(), t.usuarioModificacionId(), t.clienteNombre(), t.patente(), t.marca(), t.modelo(), t.version(), t.anio(), t.kilometraje(), t.ordenId());
        }
    }
    public record Conflicto(Long id, Long vehiculoId, LocalTime horaInicio, LocalTime horaFin,
                            String clienteNombre, String patente, String marca, String modelo) { }
    public record Disponibilidad(boolean disponible, boolean requiereConfirmacion, boolean vehiculoConTurno,
                                 List<Conflicto> conflictos, List<Conflicto> turnosDelVehiculo) { }
    public record Pagina(List<Respuesta> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
}
