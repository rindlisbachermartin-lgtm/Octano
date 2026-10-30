package com.octano.backend.talleres.controller;

import com.octano.backend.autenticacion.dto.AuthDatos;
import com.octano.backend.autenticacion.service.AuthService;
import com.octano.backend.talleres.dto.TallerRespuesta;
import com.octano.backend.talleres.dto.TallerSolicitud;
import com.octano.backend.talleres.service.TallerService;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/talleres")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
public class TallerController {
    private final TallerService servicio;
    private final AuthService registro;

    public TallerController(TallerService servicio, AuthService registro) {
        this.servicio = servicio;
        this.registro = registro;
    }

    @PostMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<AuthDatos.Sesion> crear(@Valid @RequestBody AuthDatos.Registro solicitud) {
        AuthDatos.Sesion sesion = registro.registrar(solicitud);
        return ResponseEntity.created(URI.create("/api/talleres/" + sesion.usuario().tallerId())).body(sesion);
    }

    @GetMapping
    public List<TallerRespuesta> listar() { return servicio.listar(); }

    @GetMapping("/{id}")
    public TallerRespuesta obtener(@PathVariable long id) { return servicio.obtener(id); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TallerRespuesta actualizar(@PathVariable long id, @Valid @RequestBody TallerSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }
}
