package com.octano.backend.talleres.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record TallerSolicitud(
        @NotBlank(message = "Ingresá el nombre del taller.")
        @Size(max = 120, message = "El nombre admite hasta 120 caracteres.") String nombre,
        @NotBlank(message = "Ingresá la calle.")
        @Size(max = 150, message = "La calle admite hasta 150 caracteres.") String calle,
        @NotNull(message = "Ingresá la altura.")
        @PositiveOrZero(message = "La altura no puede ser negativa.") Integer altura,
        @NotBlank(message = "Ingresá el teléfono.")
        @Pattern(regexp = "(?=.*[0-9])[+0-9() .-]{6,30}", message = "Ingresá un teléfono de entre 6 y 30 caracteres, con números y separadores.") String telefono,
        @NotBlank(message = "Ingresá el CUIT.")
        @Pattern(regexp = "[0-9]{11}", message = "El CUIT debe tener 11 números.") String cuit,
        @Size(max = 40, message = "Ingresos Brutos admite hasta 40 caracteres.") String ingresosBrutos,
        @NotNull(message = "Ingresá el inicio de actividades.")
        @PastOrPresent(message = "El inicio de actividades no puede estar en el futuro.") LocalDate inicioActividades,
        @NotNull(message = "Seleccioná una condición de IVA.")
        @Positive(message = "Seleccioná una condición de IVA válida.") Long condicionIvaId
) {
    // Limpiamos espacios y guiones antes de validar y guardar los datos.
    public TallerSolicitud {
        nombre = nombre == null ? null : nombre.strip();
        calle = calle == null ? null : calle.strip();
        telefono = telefono == null ? null : telefono.strip();
        cuit = cuit == null ? null : cuit.replace("-", "").replace(" ", "");
        ingresosBrutos = ingresosBrutos == null || ingresosBrutos.isBlank() ? null : ingresosBrutos.strip();
    }
}
