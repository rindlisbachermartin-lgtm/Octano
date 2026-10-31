package com.octano.backend.autenticacion.security;

import com.octano.backend.usuarios.entity.Rol;
import com.octano.backend.usuarios.repository.UsuarioRepository;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

    @Bean AuthenticationManager authenticationManager(UsuarioRepository usuarios, PasswordEncoder encoder) {
        UserDetailsService detalles = email -> usuarios.findByEmail(email)
                .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash())
                        .disabled(!u.getActivo()).roles(Rol.desde(u.getTipoUsuarioId()).name()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(detalles);
        proveedor.setPasswordEncoder(encoder);
        return new ProviderManager(proveedor);
    }

    @Bean SecretKey jwtKey(@Value("${octano.jwt.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET necesita al menos 32 bytes aleatorios en Base64.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean JwtDecoder jwtDecoder(SecretKey key, UsuarioRepository usuarios) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> usuarioActivo = jwt -> {
            try {
                if (usuarios.findById(Long.parseLong(jwt.getSubject())).filter(u -> u.getActivo()
                        && jwt.getClaim("version") instanceof Number version && u.getTokenVersion() == version.intValue()).isPresent()) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (NumberFormatException ignored) { }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sesión inválida.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("octano"), usuarioActivo));
        return decoder;
    }

    @Bean SecurityFilterChain apiSecurity(HttpSecurity http, UsuarioRepository usuarios, JwtDecoder decoder) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Consultamos el rol actual: una baja o cambio de permisos no espera a que venza el token.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> usuarios.findById(Long.parseLong(jwt.getSubject()))
                .filter(u -> u.getActivo())
                .map(u -> List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + Rol.desde(u.getTipoUsuarioId()).name())))
                .orElseThrow(() -> new BadCredentialsException("Sesión inválida.")));
        AuthenticationEntryPoint noAutorizado = (request, response, error) -> {
            response.setStatus(401);
            response.setContentType("application/problem+json;charset=UTF-8");
            response.getWriter().write("{\"type\":\"about:blank\",\"status\":401,\"title\":\"No autorizado\",\"detail\":\"Iniciá sesión con un token válido.\"}");
        };
        http.csrf(csrf -> csrf.disable()) // Solo Bearer: no usamos cookies ni sesiones de navegador.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/talleres", "/api/auth/registro", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/condiciones-iva", "/api/condiciones-iva/*").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(noAutorizado)
                        .accessDeniedHandler((request, response, error) -> {
                            response.setStatus(403);
                            response.setContentType("application/problem+json;charset=UTF-8");
                            response.getWriter().write("{\"type\":\"about:blank\",\"status\":403,\"title\":\"Acceso denegado\",\"detail\":\"Tu rol no permite esta operación.\"}");
                        }))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(noAutorizado)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
