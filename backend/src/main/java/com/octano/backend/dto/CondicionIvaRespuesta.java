package com.octano.backend.dto;

import com.octano.backend.entity.CondicionIva;

public record CondicionIvaRespuesta(Long id, String nombre) {
    public static CondicionIvaRespuesta desde(CondicionIva condicion) {
        return new CondicionIvaRespuesta(condicion.getId(), condicion.getNombre());
    }
}
