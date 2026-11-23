package com.octano.backend.compartido.seguridad;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LimiteIpTests {
    private static final Instant INICIO = Instant.parse("2026-01-01T00:00:00Z");
    private static final String IP = "203.0.113.5";

    @Test void permiteHastaElLimiteYCalculaElReintento() {
        LimiteIp limite = new LimiteIp(2, Duration.ofMinutes(1));

        assertTrue(limite.intentar(IP, INICIO).permitido());
        assertTrue(limite.intentar(IP, INICIO.plusSeconds(5)).permitido());
        LimiteIp.Resultado rechazo = limite.intentar(IP, INICIO.plusSeconds(10));

        assertFalse(rechazo.permitido());
        assertEquals(0, rechazo.restantes());
        assertEquals(50, rechazo.reintentarSegundos());
    }

    @Test void losRechazosNoConsumenCupoYLaVentanaSeRenueva() {
        LimiteIp limite = new LimiteIp(1, Duration.ofMinutes(1));

        assertTrue(limite.intentar(IP, INICIO).permitido());
        assertFalse(limite.intentar(IP, INICIO.plusSeconds(1)).permitido());
        assertFalse(limite.intentar(IP, INICIO.plusSeconds(59)).permitido());
        assertTrue(limite.intentar(IP, INICIO.plusSeconds(60)).permitido());
    }

    @Test void cadaIpTieneSuPropioCupo() {
        LimiteIp limite = new LimiteIp(1, Duration.ofMinutes(1));

        assertTrue(limite.intentar("198.51.100.1", INICIO).permitido());
        assertFalse(limite.intentar("198.51.100.1", INICIO).permitido());
        assertTrue(limite.intentar("198.51.100.2", INICIO).permitido());
    }
}
