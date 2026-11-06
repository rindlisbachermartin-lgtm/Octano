package com.octano.backend.repuestos.controller;

import com.octano.backend.repuestos.dto.RepuestosDatos.*;
import com.octano.backend.repuestos.service.RepuestoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repuestos")
@PreAuthorize("hasRole('ADMIN')")
public class RepuestoController {
    private final RepuestoService servicio;
    public RepuestoController(RepuestoService servicio) { this.servicio = servicio; }
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION', 'MECANICO')")
    public Pagina listar(@RequestParam(defaultValue = "") @Size(max = 250) String q,
            @RequestParam(defaultValue = "true") Boolean activo, @RequestParam(required = false) Boolean stockBajo,
            @RequestParam(required = false) @Positive Long modeloId, @RequestParam(required = false) @Positive Long versionId,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(q, activo, stockBajo, modeloId, versionId, pagina, tamano);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION', 'MECANICO')")
    public Respuesta obtener(@PathVariable long id) { return servicio.obtener(id); }
    @PostMapping
    public ResponseEntity<Respuesta> crear(@Valid @RequestBody Solicitud solicitud) {
        var repuesto = servicio.guardar(null, solicitud);
        return ResponseEntity.created(URI.create("/api/repuestos/" + repuesto.id())).body(repuesto);
    }
    @PutMapping("/{id}")
    public Respuesta editar(@PathVariable long id, @Valid @RequestBody Solicitud solicitud) { return servicio.guardar(id, solicitud); }
    @PatchMapping("/{id}/estado")
    public Respuesta estado(@PathVariable long id, @Valid @RequestBody Estado solicitud) { return servicio.estado(id, solicitud.activo()); }
    @GetMapping("/{id}/compatibilidades")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION', 'MECANICO')")
    public List<Compatibilidad> compatibilidades(@PathVariable long id) { return servicio.compatibilidades(id); }
    @PostMapping("/{id}/compatibilidades")
    public ResponseEntity<Compatibilidad> agregar(@PathVariable long id, @Valid @RequestBody CompatibilidadSolicitud solicitud) {
        var compatibilidad = servicio.agregarCompatibilidad(id, solicitud);
        return ResponseEntity.created(URI.create("/api/repuestos/" + id + "/compatibilidades/" + compatibilidad.id())).body(compatibilidad);
    }
    @DeleteMapping("/{id}/compatibilidades/{compatibilidadId}")
    public ResponseEntity<Void> quitar(@PathVariable long id, @PathVariable long compatibilidadId) {
        servicio.quitarCompatibilidad(id, compatibilidadId);
        return ResponseEntity.noContent().build();
    }
}
