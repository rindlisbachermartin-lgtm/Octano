package com.octano.backend.codigosqr.controller;

import com.octano.backend.codigosqr.dto.QrDatos.*;
import com.octano.backend.codigosqr.service.QrService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/codigos-qr")
@PreAuthorize("hasRole('ADMIN')")
public class QrController {
    private final QrService servicio;
    public QrController(QrService servicio) { this.servicio=servicio; }
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Pagina listar(@RequestParam(defaultValue="") @Size(max=250) String q,
            @RequestParam(defaultValue="DISPONIBLE") Estado estado,@RequestParam(required=false) Boolean impreso,
            @RequestParam(required=false) @Positive Long vehiculoId,@RequestParam(defaultValue="0") @Min(0) int pagina,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(q,estado,impreso,vehiculoId,pagina,tamano);
    }
    @GetMapping("/{codigo}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public Codigo obtener(@PathVariable String codigo) { return servicio.obtener(codigo); }
    @PostMapping("/lotes")
    public ResponseEntity<List<Codigo>> generar() { return ResponseEntity.status(HttpStatus.CREATED).body(servicio.generarLote()); }
    @PutMapping("/{codigo}/vehiculo")
    public Codigo asignar(@PathVariable String codigo,@Valid @RequestBody Asignacion s) { return servicio.asignar(codigo,s.vehiculoId()); }
    @DeleteMapping("/{codigo}/vehiculo")
    public Codigo desvincular(@PathVariable String codigo) { return servicio.desvincular(codigo); }
    @PostMapping("/impresiones")
    public List<Codigo> imprimir(@Valid @RequestBody Impresion s) { return servicio.imprimir(s.codigos()); }
    @PostMapping("/plantillas")
    public List<Codigo> plantilla(@Valid @RequestBody Impresion s) { return servicio.plantilla(s.codigos()); }
    @GetMapping(value="/{codigo}/imagen",produces=MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
    public ResponseEntity<byte[]> imagen(@PathVariable String codigo,@RequestParam(defaultValue="280") @Min(200) @Max(1024) int tamano) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(servicio.imagen(codigo,tamano));
    }
}
