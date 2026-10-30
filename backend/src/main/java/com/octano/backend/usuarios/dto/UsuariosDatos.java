package com.octano.backend.usuarios.dto;

import com.octano.backend.usuarios.entity.Rol;
import com.octano.backend.usuarios.entity.Usuario;
import jakarta.validation.constraints.*;
public final class UsuariosDatos {
    private UsuariosDatos() { }
    public record Alta(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=12,max=72) String password,
                       @NotNull Rol rol, @Positive Long mecanicoId) { }
    public record Estado(@NotNull Boolean activo) { }
    public record Respuesta(Long id, String email, Rol rol, Long tallerId, Long mecanicoId, boolean activo) {
        public static Respuesta desde(Usuario u) {
            return new Respuesta(u.getId(), u.getEmail(), Rol.desde(u.getTipoUsuarioId()), u.getTallerId(), u.getMecanicoId(), u.getActivo());
        }
    }
}
