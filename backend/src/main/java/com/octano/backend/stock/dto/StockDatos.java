package com.octano.backend.stock.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class StockDatos {
    private StockDatos() { }
    public enum Tipo { ENTRADA, SALIDA, AJUSTE }
    public record Solicitud(@NotNull Tipo tipo,
                            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal cantidad,
                            @NotBlank @Size(max = 250) String motivo) {
        public Solicitud { motivo = motivo == null ? null : motivo.strip(); }
    }
    public record Movimiento(Long id, Tipo tipo, BigDecimal cantidad, BigDecimal variacion,
                              BigDecimal stockAnterior, BigDecimal stockPosterior, String motivo,
                              Long usuarioId, Instant fecha) { }
    public record Pagina(List<Movimiento> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
}
