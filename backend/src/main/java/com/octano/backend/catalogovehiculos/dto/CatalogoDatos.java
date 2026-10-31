package com.octano.backend.catalogovehiculos.dto;
import jakarta.validation.constraints.*;
public final class CatalogoDatos {
    private CatalogoDatos() { }
    private static String normalizar(String valor) { return valor == null ? null : valor.strip().replaceAll("\\s+", " "); }
    public record MarcaSolicitud(@NotBlank @Size(max=80) String nombre) { public MarcaSolicitud { nombre = normalizar(nombre); } }
    public record ModeloSolicitud(@NotNull @Positive Long marcaId,@NotBlank @Size(max=80) String nombre) { public ModeloSolicitud { nombre = normalizar(nombre); } }
    public record VersionSolicitud(@NotNull @Positive Long modeloId,@NotBlank @Size(max=80) String nombre) { public VersionSolicitud { nombre = normalizar(nombre); } }
    public record Opcion(Long id,String nombre,Long padreId) { }
}
