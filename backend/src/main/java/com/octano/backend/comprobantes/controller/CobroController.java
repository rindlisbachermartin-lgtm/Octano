package com.octano.backend.comprobantes.controller;

import com.octano.backend.comprobantes.dto.ComprobantesDatos.PaginaCobros;
import com.octano.backend.comprobantes.entity.Comprobante.MedioPago;
import com.octano.backend.comprobantes.service.ComprobanteService;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cobros")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
public class CobroController {
    private final ComprobanteService servicio;
    public CobroController(ComprobanteService servicio) { this.servicio=servicio; }
    @GetMapping
    public PaginaCobros listar(@RequestParam(required=false) MedioPago medioPago,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue="0") @Min(0) int pagina,@RequestParam(defaultValue="20") @Min(1) @Max(100) int tamano) {
        return servicio.cobros(desde,hasta,medioPago,pagina,tamano);
    }
}
