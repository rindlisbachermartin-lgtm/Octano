package com.octano.backend.autenticacion.controller;

import com.octano.backend.autenticacion.dto.AuthDatos;
import com.octano.backend.autenticacion.service.AuthService;
import com.octano.backend.usuarios.dto.UsuariosDatos;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService servicio;
    public AuthController(AuthService servicio) { this.servicio = servicio; }
    @PostMapping("/registro") @ResponseStatus(HttpStatus.CREATED)
    public AuthDatos.Sesion registrar(@Valid @RequestBody AuthDatos.Registro solicitud) { return servicio.registrar(solicitud); }
    @PostMapping("/login")
    public AuthDatos.Sesion login(@Valid @RequestBody AuthDatos.Login solicitud) { return servicio.login(solicitud); }
    @GetMapping("/me")
    public UsuariosDatos.Respuesta actual() { return servicio.actual(); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() { servicio.logout(); }
    @PutMapping("/password")
    public AuthDatos.Sesion cambiarPassword(@Valid @RequestBody AuthDatos.CambioPassword solicitud) { return servicio.cambiarPassword(solicitud); }
}
