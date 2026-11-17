package com.octano.backend.mecanicos.dto;

import com.octano.backend.mecanicos.entity.Mecanico;
import java.time.LocalDate;

public record MecanicoRespuesta(Long id, String nombre, String apellido, String telefono,
                                String calle, String cuit, LocalDate fechaInicio, boolean activo) {
    public static MecanicoRespuesta desde(Mecanico mecanico) {
        return new MecanicoRespuesta(mecanico.getId(), mecanico.getNombre(), mecanico.getApellido(),
                mecanico.getTelefono(), mecanico.getCalle(), mecanico.getCuit(),
                mecanico.getFechaInicio(), mecanico.getActivo());
    }
}
