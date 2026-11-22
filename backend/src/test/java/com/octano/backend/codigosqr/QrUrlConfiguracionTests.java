package com.octano.backend.codigosqr;

import com.octano.backend.codigosqr.service.QrService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class QrUrlConfiguracionTests {
    @Test void produccionRechazaDireccionesLocales() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        for (String host : new String[]{"localhost", "127.0.0.1", "[::1]", "0.0.0.0", "taller.localhost"}) {
            assertThrows(IllegalStateException.class,
                () -> new QrService(null, null, null, null, null, "http://" + host + ":3000", environment));
        }
        assertDoesNotThrow(() -> new QrService(null, null, null, null, null, "https://taller.example", environment));
    }

    @Test void desarrolloPermiteLocalhost() {
        assertDoesNotThrow(() -> new QrService(null, null, null, null, null, "http://localhost:3000", new MockEnvironment()));
    }
}
