package com.octano.backend.mecanicos.controller;

import com.octano.backend.mecanicos.dto.EstadoMecanicoSolicitud;
import com.octano.backend.mecanicos.dto.MecanicoRespuesta;
import com.octano.backend.mecanicos.dto.MecanicoSolicitud;
import com.octano.backend.mecanicos.service.MecanicoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mecanicos")
@PreAuthorize("hasRole('ADMIN')")
public class MecanicoController {
    private final MecanicoService servicio;

    public MecanicoController(MecanicoService servicio) { this.servicio = servicio; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public List<MecanicoRespuesta> listar(@RequestParam(required = false) Boolean activo) {
        return servicio.listar(activo);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public MecanicoRespuesta obtener(@PathVariable long id) { return servicio.obtener(id); }

    @PostMapping
    public ResponseEntity<MecanicoRespuesta> crear(@Valid @RequestBody MecanicoSolicitud solicitud) {
        MecanicoRespuesta mecanico = servicio.crear(solicitud);
        return ResponseEntity.created(URI.create("/api/mecanicos/" + mecanico.id())).body(mecanico);
    }

    @PutMapping("/{id}")
    public MecanicoRespuesta actualizar(@PathVariable long id, @Valid @RequestBody MecanicoSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/estado")
    public MecanicoRespuesta cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoMecanicoSolicitud solicitud) {
        return servicio.cambiarEstado(id, solicitud.activo());
    }
}
