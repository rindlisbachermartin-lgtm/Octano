package com.octano.backend.clientes.dto;

import com.octano.backend.clientes.entity.Cliente;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class ClientesDatos {
    private ClientesDatos() { }
    public record Solicitud(
            @NotBlank(message = "Ingresá el nombre o razón social.") @Size(min = 2, max = 90) String nombre,
            @NotBlank(message = "Ingresá el DNI o CUIT/CUIL.")
            @Pattern(regexp = "([0-9]{7,8}|[0-9]{11})", message = "Ingresá un DNI de 7 u 8 números o un CUIT/CUIL de 11.") String documento,
            @NotBlank(message = "Ingresá el teléfono.")
            @Pattern(regexp = "(?=.*[0-9])[+0-9() .-]{6,30}", message = "Ingresá un teléfono de entre 6 y 30 caracteres, con números y separadores.") String telefono,
            @Email(message = "Ingresá un email válido.") @Size(max = 254) String email,
            @Size(max = 150) String calle, @PositiveOrZero Integer altura,
            @NotNull(message = "Seleccioná una condición de IVA.") @Positive Long condicionIvaId) {
        public Solicitud {
            nombre = nombre == null ? null : nombre.strip();
            documento = documento == null ? null : documento.replaceAll("[ .-]", "");
            telefono = telefono == null ? null : telefono.strip();
            email = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
            calle = calle == null || calle.isBlank() ? null : calle.strip();
        }
    }
    public record Estado(@NotNull(message = "Indicá si el cliente está activo.") Boolean activo) { }
    public record Respuesta(Long id, String nombre, String documento, String telefono, String email,
                            String calle, Integer altura, Long condicionIvaId, boolean activo,
                            Instant fechaModif, Long usuarioModificacionId) {
        public static Respuesta desde(Cliente c) {
            return new Respuesta(c.getId(), c.getNombre(), c.getDocumento(), c.getTelefono(), c.getEmail(),
                    c.getCalle(), c.getAltura(), c.getCondicionIvaId(), c.getActivo(), c.getFechaModif(), c.getUsuarioModificacionId());
        }
    }
    public record Pagina(List<Respuesta> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) { }
}
