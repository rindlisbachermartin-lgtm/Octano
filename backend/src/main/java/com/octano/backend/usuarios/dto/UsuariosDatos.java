package com.octano.backend.usuarios.dto;

import com.octano.backend.usuarios.entity.Rol;
import com.octano.backend.usuarios.entity.Usuario;
import com.octano.backend.compartido.validation.FormatoPersonal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public final class UsuariosDatos {
    private UsuariosDatos() { }
    public record DatosPersonales(
            @NotBlank(message = "Ingresá el nombre.") @Size(min = 2, max = 90, message = "El nombre debe tener entre 2 y 90 caracteres.")
            @Pattern(regexp = FormatoPersonal.NOMBRE, message = "Ingresá un nombre con letras, sin números.") String nombre,
            @NotBlank(message = "Ingresá el apellido.") @Size(max = 90, message = "El apellido admite hasta 90 caracteres.")
            @Pattern(regexp = FormatoPersonal.NOMBRE, message = "Ingresá un apellido con letras, sin números.") String apellido,
            @NotBlank(message = "Ingresá el teléfono.")
            @Pattern(regexp = FormatoPersonal.TELEFONO,
                    message = "Ingresá un teléfono de 10 a 15 números, con código de área.") String telefono,
            @NotNull(message = "Ingresá la fecha de inicio.")
            @PastOrPresent(message = "La fecha de inicio no puede estar en el futuro.") LocalDate fechaInicio) {
        public DatosPersonales {
            nombre = nombre == null ? null : nombre.strip();
            apellido = apellido == null ? null : apellido.strip();
            telefono = telefono == null ? null : telefono.strip();
        }
    }

    public record Alta(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=12,max=72) String password,
                       @NotNull Rol rol, @Positive Long mecanicoId, @Valid DatosPersonales datosPersonales) {
        public Alta(String email, String password, Rol rol, Long mecanicoId) {
            this(email, password, rol, mecanicoId, null);
        }
    }
    public record Edicion(@NotBlank @Email @Size(max = 254) String email, @Valid DatosPersonales datosPersonales) { }
    public record Estado(@NotNull Boolean activo) { }
    public enum TipoCuenta { DUENO, ADMIN, MECANICO, RECEPCION }

    public record Respuesta(Long id, String email, TipoCuenta rol, Long tallerId, Long mecanicoId, boolean activo,
                            DatosPersonales datosPersonales, boolean dueno) {
        public static Respuesta desde(Usuario u) {
            var datos = u.getNombre() == null ? null :
                    new DatosPersonales(u.getNombre(), u.getApellido(), u.getTelefono(), u.getFechaInicio());
            var tipo = u.getDueno() ? TipoCuenta.DUENO : TipoCuenta.valueOf(Rol.desde(u.getTipoUsuarioId()).name());
            return new Respuesta(u.getId(), u.getEmail(), tipo, u.getTallerId(), u.getMecanicoId(), u.getActivo(), datos, u.getDueno());
        }
    }
}
