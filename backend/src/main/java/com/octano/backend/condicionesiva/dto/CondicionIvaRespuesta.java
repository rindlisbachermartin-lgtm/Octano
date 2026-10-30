package com.octano.backend.condicionesiva.dto;

import com.octano.backend.condicionesiva.entity.CondicionIva;


public record CondicionIvaRespuesta(Long id, String nombre) {
    public static CondicionIvaRespuesta desde(CondicionIva condicion) {
        return new CondicionIvaRespuesta(condicion.getId(), condicion.getNombre());
    }
}
