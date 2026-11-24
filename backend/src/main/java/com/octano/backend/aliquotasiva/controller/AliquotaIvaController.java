package com.octano.backend.aliquotasiva.controller;

import com.octano.backend.aliquotasiva.dto.AliquotaIvaRespuesta;
import com.octano.backend.aliquotasiva.service.AliquotaIvaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aliquotas-iva")
public class AliquotaIvaController {
    private final AliquotaIvaService servicio;
    public AliquotaIvaController(AliquotaIvaService servicio) { this.servicio=servicio; }
    @GetMapping public List<AliquotaIvaRespuesta> listar() { return servicio.listar(); }
}
