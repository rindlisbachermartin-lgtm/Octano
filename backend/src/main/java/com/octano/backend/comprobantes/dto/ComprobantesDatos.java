package com.octano.backend.comprobantes.dto;

import com.octano.backend.comprobantes.entity.Comprobante;
import com.octano.backend.comprobantes.entity.Comprobante.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ComprobantesDatos {
    private ComprobantesDatos() { }
    public record ItemSolicitud(@Positive Long repuestoId, @Size(max=250) String descripcion,
            @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=3) BigDecimal cantidad,
            @PositiveOrZero @Digits(integer=12,fraction=2) BigDecimal precioUnitario) {
        public ItemSolicitud { descripcion=descripcion==null?"":descripcion.strip(); }
    }
    public record Solicitud(@Positive Long ordenId, @NotNull @Positive Long vehiculoId,
            @NotBlank @Size(max=2000) String descripcion,
            @NotNull @PositiveOrZero @Digits(integer=12,fraction=2) BigDecimal manoObra,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal porcentajeIva,
            @NotNull @Size(max=200) List<@NotNull @Valid ItemSolicitud> items) {
        public Solicitud {
            descripcion=descripcion==null?"":descripcion.strip();
            porcentajeIva=porcentajeIva==null?new BigDecimal("21.00"):porcentajeIva;
        }
    }
    public record CobroSolicitud(@NotNull MedioPago medioPago, @Size(max=1000) String observaciones) {
        public CobroSolicitud { observaciones=observaciones==null?"":observaciones.strip(); }
    }
    public record Resumen(Long id, Long ordenId, Long clienteId, Long vehiculoId, LocalDate fecha, String descripcion,
            Estado estado, Long numero, BigDecimal total, String clienteNombre, String patente, String marca, String modelo) {
        public static Resumen desde(Comprobante c) {
            return new Resumen(c.id(),c.ordenId(),c.clienteId(),c.vehiculoId(),c.fecha(),c.descripcion(),c.estado(),c.numero(),
                    c.total(),c.receptor().nombre(),c.vehiculo().patente(),c.vehiculo().marca(),c.vehiculo().modelo());
        }
    }
    public record Respuesta(Long id, Long ordenId, Long clienteId, Long vehiculoId, LocalDate fecha, String descripcion,
            Estado estado, String tipo, boolean esFiscal, Long numero, BigDecimal manoObra, BigDecimal porcentajeIva,
            BigDecimal totalRepuestos, BigDecimal subtotal, BigDecimal montoIva, BigDecimal total, Parte emisor, Parte receptor,
            Vehiculo vehiculo, Instant creadoEn, Long creadoPor, Instant emitidoEn, Long emitidoPor,
            Instant fechaModif, Long usuarioModificacionId, List<Item> items, Cobro cobro) {
        public static Respuesta desde(Comprobante c, List<Item> items, Cobro cobro) {
            return new Respuesta(c.id(),c.ordenId(),c.clienteId(),c.vehiculoId(),c.fecha(),c.descripcion(),c.estado(),"X",false,
                    c.numero(),c.manoObra(),c.porcentajeIva(),c.totalRepuestos(),c.subtotal(),c.montoIva(),c.total(),c.emisor(),c.receptor(),
                    c.vehiculo(),c.creadoEn(),c.creadoPor(),c.emitidoEn(),c.emitidoPor(),c.fechaModif(),c.usuarioModificacionId(),items,cobro);
        }
    }
    public record Pendiente(Long ordenId, Long clienteId, Long vehiculoId, String clienteNombre, String patente,
            String marca, String modelo, String descripcion, Long presupuestoId, Instant finalizadaEn, BigDecimal total) { }
    public record Pagina(List<Resumen> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
    public record PaginaPendientes(List<Pendiente> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
    public record PaginaCobros(List<Cobro> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
}
