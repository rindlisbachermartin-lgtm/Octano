package com.octano.backend.repuestos.dto;

import com.octano.backend.repuestos.entity.Repuesto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class RepuestosDatos {
    private RepuestosDatos() { }
    public record Solicitud(
            @NotBlank @Size(max = 80) String codigoOem,
            @NotBlank @Size(max = 250) String descripcion,
            @NotBlank @Size(max = 80) String marca,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal precioCosto,
            @NotNull @PositiveOrZero @Digits(integer = 4, fraction = 2) BigDecimal margen,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal stockMinimo) {
        public Solicitud {
            codigoOem = codigoOem == null ? null : codigoOem.strip().toUpperCase(Locale.ROOT);
            descripcion = descripcion == null ? null : descripcion.strip();
            marca = marca == null ? null : marca.strip();
        }
    }
    public record Estado(@NotNull Boolean activo) { }
    public record CompatibilidadSolicitud(@NotNull @Positive Long modeloId, @Positive Long versionId) { }
    public record Compatibilidad(Long id, Long modeloId, Long versionId, Long marcaId, String marca, String modelo, String version) { }
    public record Respuesta(Long id, String codigoOem, String descripcion, String marca,
                            BigDecimal precioCosto, BigDecimal margen, BigDecimal precioPublico,
                            BigDecimal stock, BigDecimal stockMinimo, boolean stockBajo, boolean activo,
                            Instant fechaModif, Long usuarioModificacionId) {
        public static Respuesta desde(Repuesto r) {
            return new Respuesta(r.id(), r.codigoOem(), r.descripcion(), r.marca(), r.precioCosto(), r.margen(),
                    r.precioPublico(), r.stock(), r.stockMinimo(), r.stock().compareTo(r.stockMinimo()) <= 0,
                    r.activo(), r.fechaModif(), r.usuarioModificacionId());
        }
    }
    public record Pagina(List<Respuesta> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
}
