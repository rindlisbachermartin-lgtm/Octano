package com.octano.backend.usuarios.controller;

import com.octano.backend.usuarios.dto.UsuariosDatos;
import com.octano.backend.usuarios.entity.Rol;
import com.octano.backend.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/usuarios") @PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {
    private final UsuarioService servicio;
    public UsuarioController(UsuarioService servicio) { this.servicio=servicio; }
    @GetMapping public List<UsuariosDatos.Respuesta> listar() { return servicio.listar(); }
    @GetMapping("/{id}") public UsuariosDatos.Respuesta obtener(@PathVariable long id) { return servicio.obtener(id); }
    @GetMapping("/roles") public Rol[] roles() { return Rol.values(); }
    @PostMapping public ResponseEntity<UsuariosDatos.Respuesta> crear(@Valid @RequestBody UsuariosDatos.Alta solicitud) {
        var usuario=servicio.crear(solicitud);
        return ResponseEntity.created(URI.create("/api/usuarios/"+usuario.id())).body(usuario);
    }
    @PatchMapping("/{id}/estado") public UsuariosDatos.Respuesta estado(@PathVariable long id,@Valid @RequestBody UsuariosDatos.Estado solicitud) {
        return servicio.cambiarEstado(id,solicitud.activo());
    }
}
