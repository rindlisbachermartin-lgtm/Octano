package com.octano.backend.compartido.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana fija en memoria, por clave (normalmente una IP). No comparte estado entre reinicios.
 * Las ventanas viejas se descartan cuando el mapa crece para no retener direcciones sin uso.
 */
public final class LimiteIp {
    private static final int MAX_CLAVES = 10_000;

    private final int limite;
    private final long ventanaSegundos;
    private final ConcurrentHashMap<String, Ventana> contadores = new ConcurrentHashMap<>();

    private record Ventana(long inicio, int usos) { }

    public record Resultado(boolean permitido, int restantes, long reintentarSegundos) { }

    public LimiteIp(int limite, Duration ventana) {
        if (limite <= 0) throw new IllegalArgumentException("El límite debe ser positivo.");
        if (ventana == null || ventana.isZero() || ventana.isNegative()) {
            throw new IllegalArgumentException("La ventana debe ser positiva.");
        }
        this.limite = limite;
        this.ventanaSegundos = Math.max(1, ventana.toSeconds());
    }

    public int limite() {
        return limite;
    }

    public Resultado intentar(String clave, Instant ahora) {
        long segundo = ahora.getEpochSecond();
        boolean[] permitido = new boolean[1];
        Ventana actual = contadores.compute(clave, (ignorada, anterior) -> {
            if (anterior == null || segundo - anterior.inicio() >= ventanaSegundos) {
                permitido[0] = true;
                return new Ventana(segundo, 1);
            }
            permitido[0] = anterior.usos() < limite;
            return new Ventana(anterior.inicio(), permitido[0] ? anterior.usos() + 1 : anterior.usos());
        });
        if (contadores.size() > MAX_CLAVES) limpiar(segundo);
        long reintentar = Math.max(1, actual.inicio() + ventanaSegundos - segundo);
        return new Resultado(permitido[0], Math.max(0, limite - actual.usos()), reintentar);
    }

    private void limpiar(long segundo) {
        contadores.entrySet().removeIf(entrada -> segundo - entrada.getValue().inicio() >= ventanaSegundos);
    }
}
