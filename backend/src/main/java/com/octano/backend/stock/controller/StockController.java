package com.octano.backend.stock.controller;

import com.octano.backend.stock.dto.StockDatos.*;
import com.octano.backend.stock.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repuestos/{repuestoId}/movimientos")
@PreAuthorize("hasRole('ADMIN')")
public class StockController {
    private final StockService servicio;
    public StockController(StockService servicio) { this.servicio = servicio; }
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public Pagina listar(@PathVariable long repuestoId, @RequestParam(defaultValue = "0") @Min(0) int pagina,
                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(repuestoId, pagina, tamano);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public Movimiento obtener(@PathVariable long repuestoId, @PathVariable long id) { return servicio.obtener(repuestoId, id); }
    @PostMapping
    public ResponseEntity<Movimiento> mover(@PathVariable long repuestoId, @Valid @RequestBody Solicitud solicitud) {
        var movimiento = servicio.mover(repuestoId, solicitud);
        return ResponseEntity.created(URI.create("/api/repuestos/" + repuestoId + "/movimientos/" + movimiento.id())).body(movimiento);
    }
}
