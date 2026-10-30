package com.octano.backend.autenticacion.security;

import com.octano.backend.usuarios.entity.Usuario;
import com.octano.backend.usuarios.repository.UsuarioRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class SesionActual {
    private final UsuarioRepository usuarios;
    public SesionActual(UsuarioRepository usuarios) { this.usuarios = usuarios; }
    public Usuario usuario() {
        return usuarios.findById(usuarioId()).filter(Usuario::getActivo)
                .orElseThrow(() -> new BadCredentialsException("Sesión inválida."));
    }
    public long tallerId() { return usuario().getTallerId(); }
    public long usuarioId() { return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName()); }
}
