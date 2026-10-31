package com.octano.backend.catalogovehiculos.controller;
import com.octano.backend.catalogovehiculos.dto.CatalogoDatos.*;
import com.octano.backend.catalogovehiculos.service.CatalogoVehiculoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/catalogo")
public class CatalogoVehiculoController {
    private final CatalogoVehiculoService servicio;
    public CatalogoVehiculoController(CatalogoVehiculoService servicio) { this.servicio=servicio; }
    @GetMapping("/marcas") public List<Opcion> marcas() { return servicio.marcas(); }
    @GetMapping("/modelos") public List<Opcion> modelos(@RequestParam @Positive long marcaId) { return servicio.modelos(marcaId); }
    @GetMapping("/versiones") public List<Opcion> versiones(@RequestParam @Positive long modeloId) { return servicio.versiones(modeloId); }
    @PostMapping("/marcas") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public Opcion marca(@Valid @RequestBody MarcaSolicitud s) { return servicio.crearMarca(s); }
    @PostMapping("/modelos") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public Opcion modelo(@Valid @RequestBody ModeloSolicitud s) { return servicio.crearModelo(s); }
    @PostMapping("/versiones") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public Opcion version(@Valid @RequestBody VersionSolicitud s) { return servicio.crearVersion(s); }
}
