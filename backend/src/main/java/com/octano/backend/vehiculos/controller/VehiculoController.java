package com.octano.backend.vehiculos.controller;
import com.octano.backend.vehiculos.dto.VehiculosDatos.*;
import com.octano.backend.vehiculos.service.VehiculoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/vehiculos") @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
public class VehiculoController {
    private final VehiculoService servicio;
    public VehiculoController(VehiculoService servicio) { this.servicio=servicio; }
    @GetMapping public Pagina listar(@RequestParam(required=false) @Positive Long clienteId,
        @RequestParam(required=false) Boolean activo,@RequestParam(defaultValue="") @Size(max=10) String patente,
        @RequestParam(defaultValue="0") @Min(0) int pagina,@RequestParam(defaultValue="20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(clienteId,activo,patente,pagina,tamano);
    }
    @GetMapping("/{id}") public Respuesta obtener(@PathVariable long id) { return servicio.obtener(id); }
    @GetMapping("/{id}/titularidad") public List<Titularidad> historial(@PathVariable long id) { return servicio.historial(id); }
    @PostMapping public ResponseEntity<Respuesta> crear(@Valid @RequestBody Solicitud s) {
        var v=servicio.guardar(null,s); return ResponseEntity.created(URI.create("/api/vehiculos/"+v.id())).body(v);
    }
    @PutMapping("/{id}") public Respuesta editar(@PathVariable long id,@Valid @RequestBody Solicitud s) { return servicio.guardar(id,s); }
    @PatchMapping("/{id}/titular") public Respuesta titular(@PathVariable long id,@Valid @RequestBody TitularSolicitud s) { return servicio.titular(id,s.clienteId()); }
    @PatchMapping("/{id}/estado") public Respuesta estado(@PathVariable long id,@Valid @RequestBody EstadoSolicitud s) { return servicio.estado(id,s.activo()); }
}
