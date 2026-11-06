package com.octano.backend.presupuestos.dto;

import com.octano.backend.presupuestos.entity.Presupuesto;
import com.octano.backend.presupuestos.entity.Presupuesto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PresupuestosDatos {
    private PresupuestosDatos() { }
    public record ItemSolicitud(@Positive Long repuestoId, @Size(max = 250) String descripcion,
                                @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3) BigDecimal cantidad,
                                @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal precioUnitario) {
        public ItemSolicitud { descripcion = descripcion == null ? null : descripcion.strip(); }
    }
    public record Solicitud(@NotNull @Positive Long clienteId, @NotNull @Positive Long vehiculoId,
                            @NotBlank @Size(max = 2000) String descripcion,
                            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal manoObra,
                            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal porcentajeIva,
                            @NotEmpty @Size(max = 3) List<@NotNull TipoTrabajo> tiposTrabajo,
                            @NotNull @Size(max = 200) List<@NotNull @Valid ItemSolicitud> items) {
        public Solicitud {
            descripcion = descripcion == null ? null : descripcion.strip();
            porcentajeIva = porcentajeIva == null ? new BigDecimal("21.00") : porcentajeIva;
        }
    }
    public record CambioEstado(@NotNull Estado estado) { }
    public record Resumen(Long id, Long clienteId, Long vehiculoId, LocalDate fecha, String descripcion, Estado estado, BigDecimal total) {
        public static Resumen desde(Presupuesto p) { return new Resumen(p.id(), p.clienteId(), p.vehiculoId(), p.fecha(), p.descripcion(), p.estado(), p.total()); }
    }
    public record Respuesta(Long id, Long clienteId, Long vehiculoId, LocalDate fecha, String descripcion,
                            BigDecimal manoObra, BigDecimal porcentajeIva, BigDecimal totalRepuestos, BigDecimal subtotal,
                            BigDecimal montoIva, BigDecimal total, List<TipoTrabajo> tiposTrabajo, Estado estado,
                            java.time.Instant estadoFecha, Long estadoUsuarioId, java.time.Instant fechaModif,
                            Long usuarioModificacionId, Long turnoId, List<Detalle> items) {
        public static Respuesta desde(Presupuesto p, Long turnoId, List<Detalle> items) {
            return new Respuesta(p.id(), p.clienteId(), p.vehiculoId(), p.fecha(), p.descripcion(), p.manoObra(), p.porcentajeIva(),
                    p.totalRepuestos(), p.subtotal(), p.montoIva(), p.total(), p.tiposTrabajo(), p.estado(), p.estadoFecha(),
                    p.estadoUsuarioId(), p.fechaModif(), p.usuarioModificacionId(), turnoId, items);
        }
    }
    public record Pagina(List<Resumen> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
}
