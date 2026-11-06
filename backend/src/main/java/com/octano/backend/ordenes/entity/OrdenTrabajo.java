package com.octano.backend.ordenes.entity;

import com.octano.backend.presupuestos.entity.Presupuesto.TipoTrabajo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record OrdenTrabajo(Long id, Long tallerId, Long clienteId, Long vehiculoId, Long turnoId, Long presupuestoId,
        Long mecanicoId, String descripcion, List<TipoTrabajo> tiposTrabajo, String indicaciones, String diagnostico,
        String observacionesMecanico, Integer kilometraje, String aceite, List<String> filtros,
        BigDecimal manoObra, BigDecimal porcentajeIva, Estado estado, String motivoCancelacion,
        Instant fechaCreacion, Instant ingresoFecha, Instant inicioFecha, Instant finFecha, Instant estadoFecha,
        Long estadoUsuarioId, Instant fechaModif, Long usuarioModificacionId,
        String clienteNombre, String patente, String marca, String modelo, String version, String mecanicoNombre,
        LocalDate fechaTurno, LocalTime horaInicioTurno, LocalTime horaFinTurno) {
    public enum Estado { PENDIENTE_INGRESO, EN_ESPERA, EN_PROCESO, FINALIZADO, CANCELADO }
    public boolean sinIniciar() { return estado == Estado.PENDIENTE_INGRESO || estado == Estado.EN_ESPERA; }
}
