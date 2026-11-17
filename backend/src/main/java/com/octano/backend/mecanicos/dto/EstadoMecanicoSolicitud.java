package com.octano.backend.mecanicos.dto;

import jakarta.validation.constraints.NotNull;

public record EstadoMecanicoSolicitud(@NotNull(message = "Indicá si el mecánico está activo.") Boolean activo) { }
