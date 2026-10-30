package com.octano.backend;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TallerApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    // Usamos los datos que cargó Flyway, sin depender de un número de ID fijo.
    private long condicionIvaId() {
        return jdbc.queryForObject("SELECT id FROM octano.condicion_iva WHERE nombre = 'IVA Responsable Inscripto'", Long.class);
    }

    private String solicitud(long condicionIvaId) {
        return """
                {"nombre":" Taller de prueba ","calle":" Calle de prueba ","altura":100,
                 "telefono":"02392 456789","cuit":"20-12345678-6","ingresosBrutos":"000123",
                 "inicioActividades":"2020-01-01","condicionIvaId":%d}
                """.formatted(condicionIvaId);
    }

    @Test
    void listaLasCondicionesDeIvaSinDuplicarElCatalogo() throws Exception {
        // El catálogo debe tener las ocho opciones que ya usa el frontend.
        api.perform(get("/api/condiciones-iva"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));
        api.perform(get("/api/condiciones-iva/{id}", condicionIvaId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("IVA Responsable Inscripto"));
    }

    @Test
    void creaConsultaYEditaUnTallerEnPostgres() throws Exception {
        // Registramos un taller y comprobamos que se limpien los espacios y guiones.
        api.perform(post("/api/talleres").contentType(MediaType.APPLICATION_JSON).content(solicitud(condicionIvaId())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nombre").value("Taller de prueba"))
                .andExpect(jsonPath("$.cuit").value("20123456786"))
                .andExpect(jsonPath("$.telefono").value("02392 456789"))
                .andExpect(jsonPath("$.ingresosBrutos").value("000123"))
                .andExpect(jsonPath("$.condicionIva.nombre").value("IVA Responsable Inscripto"));

        long id = jdbc.queryForObject("SELECT id FROM octano.taller WHERE nombre = 'Taller de prueba'", Long.class);
        api.perform(get("/api/talleres/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        api.perform(get("/api/talleres")).andExpect(status().isOk());

        // Editamos el mismo registro y revisamos el dato guardado, no solo la respuesta HTTP.
        String edicion = solicitud(condicionIvaId()).replace(" Taller de prueba ", "Taller actualizado");
        api.perform(put("/api/talleres/{id}", id).contentType(MediaType.APPLICATION_JSON).content(edicion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Taller actualizado"));
        entityManager.flush();
        assertEquals("Taller actualizado", jdbc.queryForObject("SELECT nombre FROM octano.taller WHERE id = ?", String.class, id));
    }

    @Test
    void rechazaCamposInvalidosSinGuardarElTaller() throws Exception {
        // Probamos nombre vacío, altura negativa y una fecha futura.
        long cantidadAnterior = jdbc.queryForObject("SELECT count(*) FROM octano.taller", Long.class);
        String invalida = solicitud(condicionIvaId()).replace(" Taller de prueba ", "   ")
                .replace("02392 456789", "......")
                .replace("\"altura\":100", "\"altura\":-1")
                .replace("2020-01-01", LocalDate.now().plusDays(1).toString());
        api.perform(post("/api/talleres").contentType(MediaType.APPLICATION_JSON).content(invalida))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.errores.altura").exists())
                .andExpect(jsonPath("$.errores.telefono").exists())
                .andExpect(jsonPath("$.errores.inicioActividades").exists());
        assertEquals(cantidadAnterior, jdbc.queryForObject("SELECT count(*) FROM octano.taller", Long.class));
    }

    @Test
    void rechazaUnaAlturaConDecimalesEnLugarDeRedondearla() throws Exception {
        // Si el campo espera un entero, no queremos que 1.5 termine guardado como 1.
        api.perform(post("/api/talleres").contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud(condicionIvaId()).replace("\"altura\":100", "\"altura\":1.5")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazaCuitConDigitoVerificadorIncorrecto() throws Exception {
        // Tener once números no alcanza: el dígito de control también debe coincidir.
        for (String cuitInvalido : new String[]{"20-12345678-5", "20-99999999-9"}) {
            api.perform(post("/api/talleres").contentType(MediaType.APPLICATION_JSON)
                            .content(solicitud(condicionIvaId()).replace("20-12345678-6", cuitInvalido)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("El CUIT no tiene un dígito verificador válido."));
        }
    }

    @Test
    void informaCuandoElTallerOLaCondicionNoExisten() throws Exception {
        // Un ID inexistente se informa como 404 y no crea registros a medias.
        api.perform(get("/api/talleres/-1")).andExpect(status().isNotFound());
        api.perform(get("/api/condiciones-iva/-1")).andExpect(status().isNotFound());
        api.perform(put("/api/talleres/-1").contentType(MediaType.APPLICATION_JSON).content(solicitud(condicionIvaId())))
                .andExpect(status().isNotFound());
        api.perform(post("/api/talleres").contentType(MediaType.APPLICATION_JSON).content(solicitud(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void laBaseRechazaUnaCondicionDeIvaInexistente() {
        // Aunque alguien saltee la API, la clave foránea protege la relación.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO octano.taller (nombre, calle, altura, telefono, cuit, inicio_actividades, condicion_iva_id)
                VALUES ('Prueba de integridad', 'Calle', 100, '02392 456789', '20123456786', '2020-01-01', -1)
                """));
    }
}
