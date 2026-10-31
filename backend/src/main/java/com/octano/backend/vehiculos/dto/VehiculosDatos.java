package com.octano.backend.vehiculos.dto;
import com.octano.backend.vehiculos.entity.Vehiculo;
import java.util.List;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Locale;
public final class VehiculosDatos {
    private VehiculosDatos() { }
    public record Solicitud(@NotNull @Positive Long clienteId,@NotNull @Positive Long modeloId,@Positive Long versionId,
        @NotBlank @Pattern(regexp="([A-Z]{3}-[0-9]{3}|[A-Z]{2}-[0-9]{3}-[A-Z]{2})") String patente,
        @NotNull @Min(1900) Integer anio,@NotNull @Min(0) @Max(9999999) Integer kilometraje) {
        public Solicitud {
            if(patente!=null) {
                patente=patente.toUpperCase(Locale.ROOT).replaceAll("[ -]","");
                if(patente.matches("[A-Z]{3}[0-9]{3}")) patente=patente.substring(0,3)+"-"+patente.substring(3);
                else if(patente.matches("[A-Z]{2}[0-9]{3}[A-Z]{2}")) patente=patente.substring(0,2)+"-"+patente.substring(2,5)+"-"+patente.substring(5);
            }
        }
    }
    public record Pagina(List<Respuesta> contenido,int pagina,int tamano,long totalElementos,long totalPaginas) { }
    public record EstadoSolicitud(@NotNull Boolean activo) { }
    public record TitularSolicitud(@NotNull @Positive Long clienteId) { }
    public record Respuesta(Long id,Long clienteId,Long modeloId,Long versionId,String patente,int anio,int kilometraje,boolean activo,Instant fechaModif,Long usuarioModificacionId) {
        public static Respuesta desde(Vehiculo v) { return new Respuesta(v.getId(),v.getClienteId(),v.getModeloId(),v.getVersionId(),v.getPatente(),v.getAnio(),v.getKilometraje(),v.getActivo(),v.getFechaModif(),v.getUsuarioModificacionId()); }
    }
    public record Titularidad(Long id,Long clienteAnteriorId,Long clienteNuevoId,Long usuarioId,Instant fecha) {
    }
}
