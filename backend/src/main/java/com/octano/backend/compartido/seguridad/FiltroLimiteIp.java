package com.octano.backend.compartido.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Aplica cupos por IP antes de la autenticación; los rechazos son ProblemDetail. */
@Component
@Order(-200) // Antes de la cadena de seguridad de Spring (-100).
public class FiltroLimiteIp extends OncePerRequestFilter {
    private static final String IP_VALIDA = "[0-9A-Fa-f:.]{1,45}";

    private final LimiteIp global;
    private final LimiteIp autenticacion;
    private final LimiteIp qr;
    private final Set<String> proxiesConfiables;

    public FiltroLimiteIp(
            @Value("${octano.rate-limit.global.limit:300}") int globalLimite,
            @Value("${octano.rate-limit.global.ventana:1m}") Duration globalVentana,
            @Value("${octano.rate-limit.auth.limit:10}") int authLimite,
            @Value("${octano.rate-limit.auth.ventana:1m}") Duration authVentana,
            @Value("${octano.rate-limit.qr.limit:60}") int qrLimite,
            @Value("${octano.rate-limit.qr.ventana:1m}") Duration qrVentana,
            @Value("${octano.seguridad.proxies-confiables:127.0.0.1,::1,::ffff:127.0.0.1}") String proxies) {
        this.global = new LimiteIp(globalLimite, globalVentana);
        this.autenticacion = new LimiteIp(authLimite, authVentana);
        this.qr = new LimiteIp(qrLimite, qrVentana);
        this.proxiesConfiables = Arrays.stream(proxies.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String ruta = peticion.getRequestURI();
        if (ruta == null || !ruta.startsWith("/api/")) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        LimiteIp seleccionado = limitePara(peticion.getMethod(), ruta);
        LimiteIp.Resultado resultado = seleccionado.intentar(ip(peticion), Instant.now());
        respuesta.setHeader("X-RateLimit-Limit", String.valueOf(seleccionado.limite()));
        respuesta.setHeader("X-RateLimit-Remaining", String.valueOf(resultado.restantes()));

        if (!resultado.permitido()) {
            respuesta.setHeader("Retry-After", String.valueOf(resultado.reintentarSegundos()));
            respuesta.setStatus(429);
            respuesta.setContentType("application/problem+json;charset=UTF-8");
            respuesta.getWriter().write("{\"type\":\"about:blank\",\"status\":429,"
                    + "\"title\":\"Demasiadas solicitudes\","
                    + "\"detail\":\"Llegaste al límite de solicitudes. Probá de nuevo en unos instantes.\"}");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private LimiteIp limitePara(String metodo, String ruta) {
        if ("POST".equals(metodo) && (ruta.equals("/api/auth/login") || ruta.equals("/api/auth/registro")
                || ruta.equals("/api/talleres"))) {
            return autenticacion;
        }
        if ("GET".equals(metodo) && ruta.startsWith("/api/publico/qr/")) return qr;
        return global;
    }

    private String ip(HttpServletRequest peticion) {
        String remota = peticion.getRemoteAddr();
        if (remota != null && proxiesConfiables.contains(remota)) {
            String reenviada = peticion.getHeader("X-Forwarded-For");
            if (reenviada == null || reenviada.isBlank()) reenviada = peticion.getHeader("X-Real-IP");
            if (reenviada != null) {
                String primera = reenviada.split(",", 2)[0].trim();
                if (primera.matches(IP_VALIDA)) return primera;
            }
        }
        return remota == null || remota.isBlank() ? "desconocida" : remota;
    }
}
