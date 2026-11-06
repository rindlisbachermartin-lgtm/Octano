package com.octano.backend.turnos.controller;

import com.octano.backend.turnos.dto.TurnosDatos.*;
import com.octano.backend.turnos.entity.Turno.Estado;
import com.octano.backend.turnos.service.TurnoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/turnos")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
public class TurnoController {
    private final TurnoService servicio;
    public TurnoController(TurnoService servicio) { this.servicio = servicio; }
    @GetMapping
    public Pagina listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @Positive Long clienteId, @RequestParam(required = false) @Positive Long vehiculoId,
            @RequestParam(required = false) Estado estado, @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(desde, hasta, clienteId, vehiculoId, estado, pagina, tamano);
    }
    @GetMapping("/disponibilidad")
    public Disponibilidad disponibilidad(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime horaInicio,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime horaFin,
            @RequestParam(required = false) @Positive Long excluirTurnoId,
            @RequestParam(required = false) @Positive Long vehiculoId) {
        return servicio.disponibilidad(fecha, horaInicio, horaFin, excluirTurnoId, vehiculoId);
    }
    @GetMapping("/{id}")
    public Respuesta obtener(@PathVariable long id) { return servicio.obtener(id); }
    @PostMapping
    public ResponseEntity<Respuesta> crear(@Valid @RequestBody Solicitud solicitud) {
        var turno = servicio.guardar(null, solicitud);
        return ResponseEntity.created(URI.create("/api/turnos/" + turno.id())).body(turno);
    }
    @PutMapping("/{id}")
    public Respuesta editar(@PathVariable long id, @Valid @RequestBody Solicitud solicitud) { return servicio.guardar(id, solicitud); }
    @PatchMapping("/{id}/estado")
    public Respuesta estado(@PathVariable long id, @Valid @RequestBody CambioEstado solicitud) { return servicio.estado(id, solicitud.estado()); }
}
