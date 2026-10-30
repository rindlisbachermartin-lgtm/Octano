package com.octano.backend.controller;

import com.octano.backend.dto.CondicionIvaRespuesta;
import com.octano.backend.service.CondicionIvaService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/condiciones-iva")
public class CondicionIvaController {
    private final CondicionIvaService servicio;

    public CondicionIvaController(CondicionIvaService servicio) { this.servicio = servicio; }

    @GetMapping
    public List<CondicionIvaRespuesta> listar() { return servicio.listar(); }

    @GetMapping("/{id}")
    public CondicionIvaRespuesta obtener(@PathVariable long id) { return servicio.obtener(id); }
}
