package com.octano.backend.catalogovehiculos.controller;
import com.octano.backend.catalogovehiculos.dto.CatalogoDatos.*;
import com.octano.backend.catalogovehiculos.service.CatalogoVehiculoService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/catalogo")
public class CatalogoVehiculoController {
    private final CatalogoVehiculoService servicio;
    public CatalogoVehiculoController(CatalogoVehiculoService servicio) { this.servicio=servicio; }
    @GetMapping("/marcas") public List<Opcion> marcas() { return servicio.marcas(); }
    @GetMapping("/modelos") public List<Opcion> modelos(@RequestParam @Positive long marcaId) { return servicio.modelos(marcaId); }
    @GetMapping("/versiones") public List<Opcion> versiones(@RequestParam @Positive long modeloId) { return servicio.versiones(modeloId); }
}
