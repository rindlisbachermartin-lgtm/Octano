package com.octano.backend.reportes.controller;

import com.octano.backend.reportes.dto.ReportesDatos.*;
import com.octano.backend.reportes.service.ReporteService;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasRole('ADMIN')")
public class ReporteController {
    private final ReporteService servicio;
    public ReporteController(ReporteService servicio) { this.servicio=servicio; }
    @GetMapping("/resumen")
    public Resumen resumen(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.resumen(desde,hasta);
    }
    @GetMapping("/ingresos")
    public Ingresos ingresos(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.ingresos(desde,hasta);
    }
    @GetMapping("/trabajos")
    public Trabajos trabajos(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.trabajos(desde,hasta);
    }
    @GetMapping("/repuestos")
    public Repuestos repuestos(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int limite) {
        return servicio.repuestos(desde,hasta,limite);
    }
}
