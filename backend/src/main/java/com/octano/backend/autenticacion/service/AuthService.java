package com.octano.backend.autenticacion.service;

import com.octano.backend.autenticacion.dto.AuthDatos;
import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.compartido.exception.DatosInvalidosException;
import com.octano.backend.talleres.dto.TallerRespuesta;
import com.octano.backend.talleres.service.TallerService;
import com.octano.backend.usuarios.dto.UsuariosDatos;
import com.octano.backend.usuarios.entity.Rol;
import com.octano.backend.usuarios.entity.Usuario;
import com.octano.backend.usuarios.repository.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UsuarioRepository usuarios;
    private final TallerService talleres;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final JwtEncoder jwt;
    private final SesionActual sesion;

    public AuthService(UsuarioRepository usuarios, TallerService talleres, PasswordEncoder passwords,
                       AuthenticationManager authentication, JwtEncoder jwt, SesionActual sesion) {
        this.usuarios = usuarios; this.talleres = talleres; this.passwords = passwords;
        this.authentication = authentication; this.jwt = jwt; this.sesion = sesion;
    }

    @Transactional
    public AuthDatos.Sesion registrar(AuthDatos.Registro solicitud) {
        validarPassword(solicitud.password());
        String email = normalizarEmail(solicitud.email());
        if (usuarios.findByEmail(email).isPresent()) throw new DatosInvalidosException("El correo ya está registrado.");
        TallerRespuesta taller = talleres.crear(solicitud.taller());
        Usuario usuario = new Usuario();
        usuario.setTallerId(taller.id()); usuario.setEmail(email);
        usuario.setPasswordHash(passwords.encode(solicitud.password()));
        usuario.setTipoUsuarioId(Rol.ADMIN.id()); usuario.setActivo(true);
        return emitir(usuarios.save(usuario));
    }

    @Transactional(readOnly = true)
    public AuthDatos.Sesion login(AuthDatos.Login solicitud) {
        validarPassword(solicitud.password());
        String email = normalizarEmail(solicitud.email());
        authentication.authenticate(new UsernamePasswordAuthenticationToken(email, solicitud.password()));
        return emitir(usuarios.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Credenciales inválidas.")));
    }

    @Transactional(readOnly = true)
    public UsuariosDatos.Respuesta actual() { return UsuariosDatos.Respuesta.desde(sesion.usuario()); }

    @Transactional
    public void logout() { usuarios.revocarTokens(sesion.usuario().getId()); }

    @Transactional
    public AuthDatos.Sesion cambiarPassword(AuthDatos.CambioPassword solicitud) {
        validarPassword(solicitud.actual()); validarPassword(solicitud.nueva());
        Usuario usuario = usuarios.findLockedById(sesion.usuarioId()).orElseThrow(() -> new BadCredentialsException("Sesión inválida."));
        if (!passwords.matches(solicitud.actual(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas.");
        }
        usuario.setPasswordHash(passwords.encode(solicitud.nueva()));
        usuario.revocarTokens();
        usuarios.save(usuario);
        return emitir(usuario);
    }

    private AuthDatos.Sesion emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("octano").subject(usuario.getId().toString())
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(1800)).claim("tallerId", usuario.getTallerId())
                .claim("version", usuario.getTokenVersion()).build();
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthDatos.Sesion(token, "Bearer", 1800, UsuariosDatos.Respuesta.desde(usuario));
    }

    public static String normalizarEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public static void validarPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new DatosInvalidosException("La contraseña admite hasta 72 bytes en UTF-8.");
        }
    }
}
