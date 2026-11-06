package com.octano.backend.ordenes.dto;

import com.octano.backend.ordenes.entity.OrdenTrabajo;
import com.octano.backend.presupuestos.entity.Presupuesto.TipoTrabajo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class OrdenesDatos {
    private OrdenesDatos() { }
    public record Solicitud(@NotNull @Positive Long vehiculoId, @Positive Long presupuestoId, @NotNull @Positive Long mecanicoId,
            @NotBlank @Size(max=2000) String descripcion, @NotEmpty @Size(max=3) List<@NotNull TipoTrabajo> tiposTrabajo,
            @Size(max=4000) String indicaciones,
            @NotNull @PositiveOrZero @Digits(integer=12,fraction=2) BigDecimal manoObra,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal porcentajeIva) {
        public Solicitud { descripcion=limpiar(descripcion); indicaciones=limpiar(indicaciones); }
    }
    public record Trabajo(@Size(max=4000) String diagnostico, @Min(0) @Max(9999999) Integer kilometraje,
                           @Size(max=4000) String observacionesMecanico) {
        public Trabajo { diagnostico=limpiar(diagnostico); observacionesMecanico=limpiar(observacionesMecanico); }
    }
    public record Cierre(@NotNull @Min(0) @Max(9999999) Integer kilometraje, @Size(max=4000) String observacionesMecanico,
                          @Size(max=120) String aceite, @NotNull @Size(max=4) List<@NotNull Filtro> filtros) {
        public Cierre { observacionesMecanico=limpiar(observacionesMecanico); aceite=limpiar(aceite); }
    }
    public enum Filtro { ACEITE, AIRE, HABITACULO, COMBUSTIBLE }
    public record Cancelacion(@NotBlank @Size(max=500) String motivo) { public Cancelacion { motivo=limpiar(motivo); } }
    public record TareaSolicitud(@NotBlank @Size(max=500) String descripcion) { public TareaSolicitud { descripcion=limpiar(descripcion); } }
    public record TareaEstado(@NotNull Boolean completada) { }
    public record Tarea(Long id, String descripcion, boolean completada) { }
    public record ItemSolicitud(@Positive Long repuestoId, @Size(max=250) String descripcion,
            @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=3) BigDecimal cantidad,
            @PositiveOrZero @Digits(integer=12,fraction=2) BigDecimal precioUnitario) {
        public ItemSolicitud { descripcion=limpiar(descripcion); }
    }
    public record ItemsSolicitud(@NotEmpty @Size(max=200) List<@NotNull @Valid ItemSolicitud> items) { }
    public record Item(Long id, Long repuestoId, String codigoOem, String descripcion, BigDecimal cantidad,
                        BigDecimal precioUnitario, BigDecimal subtotal, boolean presupuestado, boolean consumido) { }
    public record FotoSolicitud(@NotBlank @Size(max=80) String sector, @NotBlank @Size(max=1400000) String datos) {
        public FotoSolicitud { sector=limpiar(sector); }
    }
    public record Foto(Long id,String sector,String tipoContenido,Instant fecha,Long usuarioId) { }
    public record FotoContenido(String tipoContenido,byte[] datos) { }
    public record Cabecera(Long id,Long clienteId,Long vehiculoId,Long turnoId,Long presupuestoId,Long mecanicoId,
            String descripcion,List<TipoTrabajo> tiposTrabajo,String indicaciones,String diagnostico,String observacionesMecanico,
            Integer kilometraje,String aceite,List<String> filtros,BigDecimal manoObra,BigDecimal porcentajeIva,
            OrdenTrabajo.Estado estado,String motivoCancelacion,Instant fechaCreacion,Instant ingresoFecha,Instant inicioFecha,
            Instant finFecha,Instant estadoFecha,Long estadoUsuarioId,Instant fechaModif,Long usuarioModificacionId,
            String clienteNombre,String patente,String marca,String modelo,String version,String mecanicoNombre,
            LocalDate fechaTurno,LocalTime horaInicioTurno,LocalTime horaFinTurno) {
        public static Cabecera desde(OrdenTrabajo o) {
            return new Cabecera(o.id(),o.clienteId(),o.vehiculoId(),o.turnoId(),o.presupuestoId(),o.mecanicoId(),o.descripcion(),
                    o.tiposTrabajo(),o.indicaciones(),o.diagnostico(),o.observacionesMecanico(),o.kilometraje(),o.aceite(),o.filtros(),
                    o.manoObra(),o.porcentajeIva(),o.estado(),o.motivoCancelacion(),o.fechaCreacion(),o.ingresoFecha(),o.inicioFecha(),
                    o.finFecha(),o.estadoFecha(),o.estadoUsuarioId(),o.fechaModif(),o.usuarioModificacionId(),o.clienteNombre(),
                    o.patente(),o.marca(),o.modelo(),o.version(),o.mecanicoNombre(),o.fechaTurno(),o.horaInicioTurno(),o.horaFinTurno());
        }
    }
    public record Respuesta(Cabecera orden, List<Tarea> tareas, List<Item> items, List<Foto> fotos, int progreso,
                            BigDecimal totalRepuestos, BigDecimal subtotal, BigDecimal montoIva, BigDecimal total) { }
    public record Pagina(List<Cabecera> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
    private static String limpiar(String s) { return s==null ? "" : s.strip(); }
}
