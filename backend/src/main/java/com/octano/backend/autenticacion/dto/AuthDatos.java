package com.octano.backend.autenticacion.dto;

import com.octano.backend.talleres.dto.TallerSolicitud;
import com.octano.backend.usuarios.dto.UsuariosDatos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public final class AuthDatos {
    private AuthDatos() { }
    public record Registro(@NotNull @Valid TallerSolicitud taller, @NotBlank @Email @Size(max=254) String email,
                           @NotBlank @Size(min=12, max=72) String password) { }
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) { }
    public record CambioPassword(@NotBlank @Size(max=72) String actual, @NotBlank @Size(min=12,max=72) String nueva) { }
    public record Sesion(String tokenAcceso, String tipo, long expiraEnSegundos, UsuariosDatos.Respuesta usuario) { }
}
