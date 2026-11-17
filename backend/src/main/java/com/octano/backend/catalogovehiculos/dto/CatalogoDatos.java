package com.octano.backend.catalogovehiculos.dto;

public final class CatalogoDatos {
    private CatalogoDatos() { }
    public record Opcion(Long id, String nombre, Long padreId) { }
}
