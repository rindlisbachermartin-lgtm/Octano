package com.octano.backend.aliquotasiva;

import com.octano.backend.soporte.ApiFixture;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AliquotaIvaApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    private final List<Long> talleres=new ArrayList<>();
    private String token;
    @BeforeEach void preparar() throws Exception {
        long condicion=jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva",Long.class);
        var cuenta=ApiFixture.registrar(api,"aliquotas@prueba.test",condicion);
        talleres.add(cuenta.tallerId()); token=cuenta.bearer();
    }
    @AfterEach void limpiar() {
        new TransactionTemplate(transacciones).executeWithoutResult(estado->{
            for(long taller:talleres) {
                jdbc.update("DELETE FROM octano.usuario WHERE taller_id=?",taller);
                jdbc.update("DELETE FROM octano.taller WHERE id=?",taller);
            }
        });
    }
    @Test void listaLasAliquotasDelCatalogoEnOrden() throws Exception {
        api.perform(get("/api/aliquotas-iva").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].porcentaje").value(0))
                .andExpect(jsonPath("$[1].porcentaje").value(10.5))
                .andExpect(jsonPath("$[2].porcentaje").value(21));
    }
    @Test void requiereSesion() throws Exception {
        api.perform(get("/api/aliquotas-iva")).andExpect(status().isUnauthorized());
    }
    @Test void soloAceptaLasTresAliquotasDelCatalogo() {
        AliquotaIva.validar(new BigDecimal("21"));
        AliquotaIva.validar(new BigDecimal("21.00"));
        AliquotaIva.validar(new BigDecimal("10.5"));
        AliquotaIva.validar(BigDecimal.ZERO);
        assertThrows(com.octano.backend.compartido.exception.DatosInvalidosException.class,
                () -> AliquotaIva.validar(new BigDecimal("6")));
    }
}
