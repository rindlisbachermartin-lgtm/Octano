package com.octano.backend.mecanicos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MecanicoSolicitud(
        @NotBlank(message = "Ingresá el nombre.")
        @Size(max = 90, message = "El nombre admite hasta 90 caracteres.") String nombre,
        @NotBlank(message = "Ingresá el apellido.")
        @Size(max = 90, message = "El apellido admite hasta 90 caracteres.") String apellido,
        @NotBlank(message = "Ingresá el teléfono.")
        @Pattern(regexp = "(?=.*[0-9])[+0-9() .-]{6,30}", message = "Ingresá un teléfono de entre 6 y 30 caracteres, con números y separadores.") String telefono,
        @Size(max = 150, message = "La calle admite hasta 150 caracteres.") String calle,
        @Pattern(regexp = "[0-9]{11}", message = "El CUIT/CUIL debe tener 11 números.") String cuit,
        @NotNull(message = "Ingresá la fecha de inicio.")
        @PastOrPresent(message = "La fecha de inicio no puede estar en el futuro.") LocalDate fechaInicio
) {
    public MecanicoSolicitud {
        nombre = nombre == null ? null : nombre.strip();
        apellido = apellido == null ? null : apellido.strip();
        telefono = telefono == null ? null : telefono.strip();
        calle = calle == null || calle.isBlank() ? null : calle.strip();
        cuit = cuit == null || cuit.isBlank() ? null : cuit.replace("-", "").replace(" ", "");
    }
}
