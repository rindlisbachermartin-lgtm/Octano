package com.octano.backend.talleres;

import com.octano.backend.soporte.ApiFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class TallerApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    ApiFixture.Cuenta cuenta;
    long condicion;
    @BeforeEach void prepararCuenta() throws Exception {
        condicion=jdbc.queryForObject("SELECT id FROM octano.condicion_iva WHERE nombre = 'IVA Responsable Inscripto'",Long.class);
        cuenta=ApiFixture.registrar(api,"admin@taller.test",condicion);
    }
    @Test void listaLasCondicionesDeIvaSinDuplicarElCatalogo() throws Exception {
        // El catálogo es público para poder completar el registro de un taller.
        api.perform(get("/api/condiciones-iva")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
        api.perform(get("/api/condiciones-iva/{id}",condicion)).andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("IVA Responsable Inscripto"));
    }
    @Test void consultaYEditaSuTallerEnPostgres() throws Exception {
        // El registro creó el taller; ahora lo consultamos y editamos con su token.
        api.perform(get("/api/talleres/{id}",cuenta.tallerId()).header("Authorization",cuenta.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Taller de prueba"))
            .andExpect(jsonPath("$.cuit").value("20123456786")).andExpect(jsonPath("$.telefono").value("02392 456789"));
        api.perform(put("/api/talleres/{id}",cuenta.tallerId()).header("Authorization",cuenta.bearer())
            .contentType(MediaType.APPLICATION_JSON).content(ApiFixture.taller(condicion).replace(" Taller de prueba ","Actualizado")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Actualizado"));
        entityManager.flush();
        assertEquals("Actualizado",jdbc.queryForObject("SELECT nombre FROM octano.taller WHERE id = ?",String.class,cuenta.tallerId()));
    }
    @Test void rechazaCamposInvalidosSinModificarElTaller() throws Exception {
        // Nombre vacío, altura negativa, teléfono sin números y fecha futura deben fallar.
        String body=ApiFixture.taller(condicion).replace(" Taller de prueba ","   ").replace("\"altura\":100","\"altura\":-1")
            .replace("02392 456789","......").replace("2020-01-01",LocalDate.now().plusDays(1).toString());
        api.perform(put("/api/talleres/{id}",cuenta.tallerId()).header("Authorization",cuenta.bearer())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores.nombre").exists()).andExpect(jsonPath("$.errores.altura").exists())
            .andExpect(jsonPath("$.errores.telefono").exists()).andExpect(jsonPath("$.errores.inicioActividades").exists());
    }
    @Test void rechazaAlturasDecimales() throws Exception {
        // No se debe convertir silenciosamente 1.5 en 1.
        api.perform(put("/api/talleres/{id}",cuenta.tallerId()).header("Authorization",cuenta.bearer())
            .contentType(MediaType.APPLICATION_JSON).content(ApiFixture.taller(condicion).replace("\"altura\":100","\"altura\":1.5")))
            .andExpect(status().isBadRequest());
    }
    @Test void rechazaCuitIncorrecto() throws Exception {
        // Once dígitos con un control incorrecto siguen siendo un CUIT inválido.
        for(String cuit:new String[]{"20-12345678-5","20-99999999-9"}) {
            api.perform(put("/api/talleres/{id}",cuenta.tallerId()).header("Authorization",cuenta.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(ApiFixture.taller(condicion).replace("20-12345678-6",cuit)))
                .andExpect(status().isBadRequest());
        }
    }
    @Test void rechazaRecursosInexistentes() throws Exception {
        // Solo se pueden consultar recursos existentes del taller de la sesión.
        api.perform(get("/api/talleres/-1").header("Authorization",cuenta.bearer())).andExpect(status().isNotFound());
        api.perform(put("/api/talleres/{id}",cuenta.tallerId()).header("Authorization",cuenta.bearer())
            .contentType(MediaType.APPLICATION_JSON).content(ApiFixture.taller(Long.MAX_VALUE))).andExpect(status().isNotFound());
    }
    @Test void laClaveForaneaProtegeLaCondicionIva() {
        // La base mantiene la relación incluso si se saltea la API.
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("""
            INSERT INTO octano.taller(nombre,calle,altura,telefono,cuit,inicio_actividades,condicion_iva_id)
            VALUES ('Prueba','Calle',100,'02392 456789','20123456786','2020-01-01',-1)
            """));
    }
}
