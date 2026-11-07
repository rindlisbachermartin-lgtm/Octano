package com.octano.backend.codigosqr.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class QrDatos {
    private QrDatos() { }
    public enum Estado { DISPONIBLE, ASIGNADO }
    public record Codigo(String codigo, Estado estado, Long vehiculoId, String patente, String marca, String modelo,
                         Instant creadoEn, Instant asignadoEn, Instant impresoEn, Long impresoPor,
                         Instant fechaModif, Long usuarioModificacionId, String urlPublica) { }
    public record Pagina(List<Codigo> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
    public record Asignacion(@NotNull @Positive Long vehiculoId) { }
    public record Impresion(@NotNull @Size(min=16,max=16) List<@NotBlank @Pattern(regexp="[A-Z0-9-]{3,40}") String> codigos) { }
    public record VehiculoPublico(String patente, String marca, String modelo, String version, int anio, Integer kilometraje) { }
    public record Mantenimiento(LocalDate fecha, Integer kilometraje, String aceite, List<String> filtros, List<String> repuestos,
                                String observaciones) { }
    public record Ficha(String codigo, Estado estado, VehiculoPublico vehiculo, Mantenimiento ultimoService,
                        Mantenimiento ultimaDistribucion) { }
}
