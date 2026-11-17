package com.octano.backend.comprobantes.controller;

import com.octano.backend.comprobantes.dto.ComprobantesDatos.*;
import com.octano.backend.comprobantes.entity.Comprobante.*;
import com.octano.backend.comprobantes.service.ComprobanteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comprobantes")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
public class ComprobanteController {
    private final ComprobanteService servicio;
    public ComprobanteController(ComprobanteService servicio) { this.servicio=servicio; }
    @GetMapping
    public Pagina listar(@RequestParam(defaultValue="") @Size(max=250) String q,@RequestParam(required=false) Estado estado,
            @RequestParam(required=false) @Positive Long clienteId,@RequestParam(required=false) @Positive Long vehiculoId,
            @RequestParam(required=false) @Positive Long ordenId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue="0") @Min(0) int pagina,@RequestParam(defaultValue="20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(q,estado,clienteId,vehiculoId,ordenId,desde,hasta,pagina,tamano);
    }
    @GetMapping("/ordenes-pendientes")
    public PaginaPendientes pendientes(@RequestParam(defaultValue="") @Size(max=250) String q,
            @RequestParam(required=false) Boolean conPresupuesto,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue="0") @Min(0) int pagina,@RequestParam(defaultValue="20") @Min(1) @Max(100) int tamano) {
        return servicio.pendientes(q,desde,hasta,conPresupuesto,pagina,tamano);
    }
    @GetMapping("/{id}")
    public Respuesta obtener(@PathVariable long id) { return servicio.obtener(id); }
    @PostMapping
    public ResponseEntity<Respuesta> crear(@Valid @RequestBody Solicitud s) { return creado(servicio.guardar(null,s)); }
    private ResponseEntity<Respuesta> creado(Respuesta r) { return ResponseEntity.created(URI.create("/api/comprobantes/"+r.id())).body(r); }
    @PutMapping("/{id}")
    public Respuesta editar(@PathVariable long id,@Valid @RequestBody Solicitud s) { return servicio.guardar(id,s); }
    @PostMapping("/desde-orden/{ordenId}")
    public ResponseEntity<Respuesta> desdeOrden(@PathVariable long ordenId) { return creado(servicio.desdeOrden(ordenId)); }
    @PostMapping("/{id}/emision")
    public Respuesta emitir(@PathVariable long id) { return servicio.emitir(id); }
    @PostMapping("/{id}/cobro")
    public Respuesta cobrar(@PathVariable long id,@Valid @RequestBody CobroSolicitud s) { return servicio.cobrar(id,s); }
    @GetMapping("/{id}/cobro")
    public Cobro cobro(@PathVariable long id) { return servicio.obtenerCobro(id); }
    @PostMapping("/desde-orden/{ordenId}/cobro")
    public Respuesta cobrarOrden(@PathVariable long ordenId,@Valid @RequestBody CobroSolicitud s) { return servicio.cobrarOrden(ordenId,s); }
}
