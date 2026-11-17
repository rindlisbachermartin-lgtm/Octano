package com.octano.backend.codigosqr.controller;

import com.octano.backend.codigosqr.dto.QrDatos.Ficha;
import com.octano.backend.codigosqr.service.QrService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publico/qr")
public class FichaPublicaController {
    private final QrService servicio;
    public FichaPublicaController(QrService servicio) { this.servicio=servicio; }
    @GetMapping("/{codigo}")
    public ResponseEntity<Ficha> obtener(@PathVariable String codigo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(servicio.ficha(codigo));
    }
}
