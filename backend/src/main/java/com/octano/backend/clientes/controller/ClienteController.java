package com.octano.backend.clientes.controller;

import com.octano.backend.clientes.dto.ClientesDatos;
import com.octano.backend.clientes.service.ClienteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
public class ClienteController {
    private final ClienteService servicio;
    public ClienteController(ClienteService servicio) { this.servicio = servicio; }

    @GetMapping
    public ClientesDatos.Pagina listar(@RequestParam(defaultValue = "") @Size(max = 90) String q,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(q, activo, pagina, tamano);
    }
    @GetMapping("/{id}")
    public ClientesDatos.Respuesta obtener(@PathVariable long id) { return servicio.obtener(id); }

    @PostMapping
    public ResponseEntity<ClientesDatos.Respuesta> crear(@Valid @RequestBody ClientesDatos.Solicitud solicitud) {
        var cliente = servicio.guardar(null, solicitud);
        return ResponseEntity.created(URI.create("/api/clientes/" + cliente.id())).body(cliente);
    }
    @PutMapping("/{id}")
    public ClientesDatos.Respuesta editar(@PathVariable long id, @Valid @RequestBody ClientesDatos.Solicitud solicitud) {
        return servicio.guardar(id, solicitud);
    }
    @PatchMapping("/{id}/estado")
    public ClientesDatos.Respuesta estado(@PathVariable long id, @Valid @RequestBody ClientesDatos.Estado solicitud) {
        return servicio.cambiarEstado(id, solicitud.activo());
    }
}
