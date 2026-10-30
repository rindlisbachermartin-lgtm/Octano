package com.octano.backend.controller;
import com.octano.backend.dto.*;
import com.octano.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirements
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService servicio;
    public AuthController(AuthService servicio) { this.servicio = servicio; }
    @PostMapping("/registro") @ResponseStatus(HttpStatus.CREATED)
    public AuthDatos.Sesion registrar(@Valid @RequestBody AuthDatos.Registro solicitud) { return servicio.registrar(solicitud); }
    @PostMapping("/login")
    public AuthDatos.Sesion login(@Valid @RequestBody AuthDatos.Login solicitud) { return servicio.login(solicitud); }
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
    @GetMapping("/me")
    public UsuariosDatos.Respuesta actual() { return servicio.actual(); }
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() { servicio.logout(); }
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
    @PutMapping("/password")
    public AuthDatos.Sesion cambiarPassword(@Valid @RequestBody AuthDatos.CambioPassword solicitud) { return servicio.cambiarPassword(solicitud); }
}
