package com.octano.backend.dto;

import com.octano.backend.entity.Taller;
import java.time.LocalDate;

public record TallerRespuesta(Long id, String nombre, String calle, Integer altura, String telefono,
                              String cuit, String ingresosBrutos, LocalDate inicioActividades,
                              CondicionIvaRespuesta condicionIva) {
    public static TallerRespuesta desde(Taller taller) {
        return new TallerRespuesta(taller.getId(), taller.getNombre(), taller.getCalle(), taller.getAltura(),
                taller.getTelefono(), taller.getCuit(), taller.getIngresosBrutos(), taller.getInicioActividades(),
                CondicionIvaRespuesta.desde(taller.getCondicionIva()));
    }
}
