package com.octano.backend.aliquotasiva;

import com.octano.backend.compartido.exception.DatosInvalidosException;
import java.math.BigDecimal;
import java.util.Set;

// Catálogo único: el mismo conjunto que valida la BD y ofrece la API.
public final class AliquotaIva {
    private AliquotaIva() { }

    public static final Set<BigDecimal> VALIDAS = Set.of(
            new BigDecimal("0"), new BigDecimal("10.5"), new BigDecimal("21"));

    public static void validar(BigDecimal porcentaje) {
        // Comparación por valor: 21, 21.0 y 21.00 son la misma alícuota.
        if (porcentaje == null || VALIDAS.stream().noneMatch(v -> v.compareTo(porcentaje) == 0))
            throw new DatosInvalidosException("La alícuota de IVA debe ser 0%, 10,5% o 21%.");
    }
}
