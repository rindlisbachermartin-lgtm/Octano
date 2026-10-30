package com.octano.backend.controller;

import com.octano.backend.dto.TallerRespuesta;
import com.octano.backend.dto.TallerSolicitud;
import com.octano.backend.service.TallerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/talleres")
public class TallerController {
    private final TallerService servicio;

    public TallerController(TallerService servicio) { this.servicio = servicio; }

    @GetMapping
    public List<TallerRespuesta> listar() { return servicio.listar(); }

    @GetMapping("/{id}")
    public TallerRespuesta obtener(@PathVariable long id) { return servicio.obtener(id); }

    @PostMapping
    public ResponseEntity<TallerRespuesta> crear(@Valid @RequestBody TallerSolicitud solicitud) {
        TallerRespuesta taller = servicio.crear(solicitud);
        return ResponseEntity.created(URI.create("/api/talleres/" + taller.id())).body(taller);
    }

    @PutMapping("/{id}")
    public TallerRespuesta actualizar(@PathVariable long id, @Valid @RequestBody TallerSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }
}
