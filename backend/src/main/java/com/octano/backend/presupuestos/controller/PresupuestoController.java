package com.octano.backend.presupuestos.controller;

import com.octano.backend.presupuestos.dto.PresupuestosDatos.*;
import com.octano.backend.presupuestos.entity.Presupuesto.Estado;
import com.octano.backend.presupuestos.service.PresupuestoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/presupuestos")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
public class PresupuestoController {
    private final PresupuestoService servicio;
    public PresupuestoController(PresupuestoService servicio) { this.servicio = servicio; }
    @GetMapping
    public Pagina listar(@RequestParam(defaultValue = "") @Size(max = 250) String q,
            @RequestParam(required = false) Estado estado, @RequestParam(required = false) @Positive Long clienteId,
            @RequestParam(required = false) @Positive Long vehiculoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int pagina, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(q, estado, clienteId, vehiculoId, desde, hasta, pagina, tamano);
    }
    @GetMapping("/{id}")
    public Respuesta obtener(@PathVariable long id) { return servicio.obtener(id); }
    @PostMapping
    public ResponseEntity<Respuesta> crear(@Valid @RequestBody Solicitud solicitud) {
        var presupuesto = servicio.guardar(null, solicitud);
        return ResponseEntity.created(URI.create("/api/presupuestos/" + presupuesto.id())).body(presupuesto);
    }
    @PutMapping("/{id}")
    public Respuesta editar(@PathVariable long id, @Valid @RequestBody Solicitud solicitud) { return servicio.guardar(id, solicitud); }
    @PatchMapping("/{id}/estado")
    public Respuesta estado(@PathVariable long id, @Valid @RequestBody CambioEstado solicitud) { return servicio.estado(id, solicitud.estado()); }
}
