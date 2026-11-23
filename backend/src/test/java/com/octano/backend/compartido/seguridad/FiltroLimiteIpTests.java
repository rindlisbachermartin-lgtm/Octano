package com.octano.backend.compartido.seguridad;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltroLimiteIpTests {
    private final FiltroLimiteIp filtro = new FiltroLimiteIp(300, Duration.ofMinutes(1),
            2, Duration.ofMinutes(1), 1, Duration.ofMinutes(1), "127.0.0.1");

    private MockHttpServletResponse pasar(String metodo, String ruta, String ipRemota, String reenviada) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setRemoteAddr(ipRemota);
        if (reenviada != null) peticion.addHeader("X-Forwarded-For", reenviada + ", 10.0.0.1");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        return respuesta;
    }

    @Test void limitaElLoginPorIpConProblemDetailYCabeceras() throws Exception {
        assertEquals(200, pasar("POST", "/api/auth/login", "127.0.0.1", "203.0.113.7").getStatus());
        assertEquals(200, pasar("POST", "/api/auth/login", "127.0.0.1", "203.0.113.7").getStatus());

        MockHttpServletResponse rechazo = pasar("POST", "/api/auth/login", "127.0.0.1", "203.0.113.7");
        assertEquals(429, rechazo.getStatus());
        assertTrue(rechazo.getHeader("Retry-After").matches("[1-9][0-9]*"));
        assertEquals("0", rechazo.getHeader("X-RateLimit-Remaining"));
        assertTrue(rechazo.getContentAsString().contains("Demasiadas solicitudes"));
    }

    @Test void otraIpConservaSuCupo() throws Exception {
        pasar("POST", "/api/auth/login", "127.0.0.1", "203.0.113.7");
        pasar("POST", "/api/auth/login", "127.0.0.1", "203.0.113.7");
        assertEquals(429, pasar("POST", "/api/auth/login", "127.0.0.1", "203.0.113.7").getStatus());

        MockHttpServletResponse otra = pasar("POST", "/api/auth/login", "127.0.0.1", "203.0.113.8");
        assertEquals(200, otra.getStatus());
        assertEquals("1", otra.getHeader("X-RateLimit-Remaining"));
    }

    @Test void ignoraCabecerasDeIpSiElClienteNoEsUnProxyConfiable() throws Exception {
        pasar("POST", "/api/auth/login", "198.51.100.20", "203.0.113.9");
        pasar("POST", "/api/auth/login", "198.51.100.20", "203.0.113.10");
        assertEquals(429, pasar("POST", "/api/auth/login", "198.51.100.20", "203.0.113.11").getStatus());
    }

    @Test void laFichaQrTieneSuPropioCupo() throws Exception {
        assertEquals(200, pasar("GET", "/api/publico/qr/ABC123", "127.0.0.1", null).getStatus());
        assertEquals(429, pasar("GET", "/api/publico/qr/ABC123", "127.0.0.1", null).getStatus());
        assertEquals(200, pasar("POST", "/api/auth/login", "127.0.0.1", null).getStatus());
    }
}
