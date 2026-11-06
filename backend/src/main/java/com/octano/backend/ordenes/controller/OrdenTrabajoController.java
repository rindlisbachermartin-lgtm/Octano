package com.octano.backend.ordenes.controller;

import com.octano.backend.ordenes.dto.OrdenesDatos.*;
import com.octano.backend.ordenes.entity.OrdenTrabajo.Estado;
import com.octano.backend.ordenes.service.OrdenTrabajoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ordenes-trabajo")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
public class OrdenTrabajoController {
    private final OrdenTrabajoService servicio;
    public OrdenTrabajoController(OrdenTrabajoService servicio) { this.servicio=servicio; }
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Pagina listar(@RequestParam(defaultValue="") @Size(max=250) String q,
            @RequestParam(required=false) Estado estado, @RequestParam(required=false) @Positive Long clienteId,
            @RequestParam(required=false) @Positive Long vehiculoId, @RequestParam(required=false) @Positive Long mecanicoId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue="0") @Min(0) int pagina, @RequestParam(defaultValue="20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(q,estado,clienteId,vehiculoId,mecanicoId,desde,hasta,pagina,tamano);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Respuesta obtener(@PathVariable long id) { return servicio.obtener(id); }
    @PostMapping
    public ResponseEntity<Respuesta> crear(@Valid @RequestBody Solicitud s) {
        var r=servicio.guardar(null,s);
        return ResponseEntity.created(URI.create("/api/ordenes-trabajo/"+r.orden().id())).body(r);
    }
    @PutMapping("/{id}")
    public Respuesta editar(@PathVariable long id,@Valid @RequestBody Solicitud s) { return servicio.guardar(id,s); }
    @PostMapping("/{id}/ingreso")
    public Respuesta ingreso(@PathVariable long id) { return servicio.ingresar(id); }
    @PostMapping("/{id}/inicio")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Respuesta inicio(@PathVariable long id) { return servicio.iniciar(id); }
    @PutMapping("/{id}/trabajo")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Respuesta trabajo(@PathVariable long id,@Valid @RequestBody Trabajo s) { return servicio.trabajo(id,s); }
    @PostMapping("/{id}/finalizacion")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Respuesta finalizar(@PathVariable long id,@Valid @RequestBody Cierre s) { return servicio.finalizar(id,s); }
    @PostMapping("/{id}/cancelacion")
    public Respuesta cancelar(@PathVariable long id,@Valid @RequestBody Cancelacion s) { return servicio.cancelar(id,s.motivo()); }
    @PostMapping("/{id}/tareas")
    public Respuesta tarea(@PathVariable long id,@Valid @RequestBody TareaSolicitud s) { return servicio.agregarTarea(id,s.descripcion()); }
    @PatchMapping("/{id}/tareas/{tareaId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Respuesta tareaEstado(@PathVariable long id,@PathVariable long tareaId,@Valid @RequestBody TareaEstado s) {
        return servicio.tareaEstado(id,tareaId,s.completada());
    }
    @DeleteMapping("/{id}/tareas/{tareaId}")
    public Respuesta quitarTarea(@PathVariable long id,@PathVariable long tareaId) { return servicio.quitarTarea(id,tareaId); }
    @PostMapping("/{id}/repuestos")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Respuesta repuestos(@PathVariable long id,@Valid @RequestBody ItemsSolicitud s) { return servicio.agregarItems(id,s.items()); }
    @DeleteMapping("/{id}/repuestos/{itemId}")
    public Respuesta quitarRepuesto(@PathVariable long id,@PathVariable long itemId) { return servicio.quitarItem(id,itemId); }
    @PostMapping("/{id}/fotos")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Respuesta foto(@PathVariable long id,@Valid @RequestBody FotoSolicitud s) { return servicio.agregarFoto(id,s); }
    @GetMapping("/{id}/fotos/{fotoId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public ResponseEntity<byte[]> foto(@PathVariable long id,@PathVariable long fotoId) {
        var f=servicio.foto(id,fotoId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.tipoContenido())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").body(f.datos());
    }
    @DeleteMapping("/{id}/fotos/{fotoId}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCION','MECANICO')")
    public Respuesta quitarFoto(@PathVariable long id,@PathVariable long fotoId) { return servicio.quitarFoto(id,fotoId); }
}
