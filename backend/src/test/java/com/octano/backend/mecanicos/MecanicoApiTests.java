package com.octano.backend.mecanicos;

import com.octano.backend.soporte.ApiFixture;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MecanicoApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    private ApiFixture.Cuenta admin;
    private long condicion;
    private final JsonMapper json = new JsonMapper();
    private static final String DATOS = """
            {"nombre":" Juan ","apellido":" Pérez ","telefono":"02392 456789",
             "calle":" Calle 123 ","cuit":"20-12345678-6","fechaInicio":"2020-01-01"}
            """;

    @BeforeEach void preparar() throws Exception {
        condicion = jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva", Long.class);
        admin = ApiFixture.registrar(api, "admin@mecanicos.test", condicion);
    }

    private long crear() throws Exception {
        String respuesta = api.perform(post("/api/mecanicos").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.cuit").value("20123456786"))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private void estado(long id, boolean activo) throws Exception {
        api.perform(patch("/api/mecanicos/{id}/estado", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":" + activo + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(activo));
    }

    private String crearCuenta(String email, String rol, Long mecanicoId) throws Exception {
        api.perform(post("/api/usuarios").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"ClaveDePrueba123!","rol":"%s","mecanicoId":%s}
                        """.formatted(email, rol, mecanicoId))).andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String respuesta = api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(respuesta).get("tokenAcceso").asString();
    }

    @Test void creaConsultaYEditaUnMecanico() throws Exception {
        // Creamos un mecánico y comprobamos que la edición se guarda en PostgreSQL.
        long id = crear();
        api.perform(get("/api/mecanicos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apellido").value("Pérez"));
        api.perform(put("/api/mecanicos/{id}", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(DATOS.replace(" Juan ", "Pedro")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Pedro"));
        assertEquals("Pedro", jdbc.queryForObject("SELECT nombre FROM octano.mecanico WHERE id = ?", String.class, id));
        assertEquals(admin.tallerId(), jdbc.queryForObject("SELECT taller_id FROM octano.mecanico WHERE id = ?", Long.class, id).longValue());
    }

    @Test void filtraLosMecanicosActivosEInactivos() throws Exception {
        // Una baja conserva el registro y permite encontrarlo con el filtro de inactivos.
        long id = crear();
        estado(id, false);
        api.perform(get("/api/mecanicos").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        api.perform(get("/api/mecanicos?activo=true").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        api.perform(get("/api/mecanicos?activo=false").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        estado(id, true);
        api.perform(get("/api/mecanicos?activo=true").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test void rechazaDatosInvalidosYAceptaLosOpcionalesVacios() throws Exception {
        // Nombre, teléfono, fecha y CUIT incorrectos deben fallar sin guardar registros.
        for (String datos : new String[]{DATOS.replace(" Juan ", " "), DATOS.replace(" Pérez ", " "),
                DATOS.replace("02392 456789", "......"), DATOS.replace("2020-01-01", LocalDate.now().plusDays(1).toString()),
                DATOS.replace("20-12345678-6", "20-12345678-5"), "{}"}) {
            api.perform(post("/api/mecanicos").header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isBadRequest());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM octano.mecanico WHERE taller_id = ?", Long.class, admin.tallerId()).longValue());
        // La calle y el CUIT son opcionales.
        api.perform(post("/api/mecanicos").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(DATOS.replace(" Calle 123 ", "").replace("20-12345678-6", "")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.calle").isEmpty()).andExpect(jsonPath("$.cuit").isEmpty());
    }

    @Test void noPermiteAccederALosMecanicosDeOtroTaller() throws Exception {
        // Aunque conozca el ID, otro taller no puede consultar, editar ni desactivar al mecánico.
        long id = crear();
        var otro = ApiFixture.registrar(api, "otro@mecanicos.test", condicion);
        api.perform(get("/api/mecanicos").header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        api.perform(get("/api/mecanicos/{id}", id).header("Authorization", otro.bearer())).andExpect(status().isNotFound());
        api.perform(put("/api/mecanicos/{id}", id).header("Authorization", otro.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(DATOS)).andExpect(status().isNotFound());
        api.perform(patch("/api/mecanicos/{id}/estado", id).header("Authorization", otro.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isNotFound());
    }

    @Test void controlaLosPermisosDeCadaRol() throws Exception {
        // Recepción puede consultar; solo el administrador puede hacer cambios.
        long id = crear();
        String recepcion = crearCuenta("recepcion@mecanicos.test", "RECEPCION", null);
        api.perform(get("/api/mecanicos")).andExpect(status().isUnauthorized());
        api.perform(get("/api/mecanicos").header("Authorization", recepcion)).andExpect(status().isOk());
        api.perform(get("/api/mecanicos/{id}", id).header("Authorization", recepcion)).andExpect(status().isOk());
        api.perform(post("/api/mecanicos").header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(DATOS)).andExpect(status().isForbidden());
        api.perform(put("/api/mecanicos/{id}", id).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(DATOS)).andExpect(status().isForbidden());
        api.perform(patch("/api/mecanicos/{id}/estado", id).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isForbidden());
        String mecanico = crearCuenta("mecanico@mecanicos.test", "MECANICO", id);
        api.perform(get("/api/mecanicos").header("Authorization", mecanico)).andExpect(status().isForbidden());
    }

    @Test void laBajaDelMecanicoDesactivaSuCuentaYRevocaSuToken() throws Exception {
        // Al desactivar al mecánico también pierde acceso su usuario vinculado.
        long id = crear();
        String token = crearCuenta("vinculado@mecanicos.test", "MECANICO", id);
        long usuarioId = jdbc.queryForObject("SELECT id FROM octano.usuario WHERE mecanico_id = ?", Long.class, id);
        estado(id, false);
        assertFalse(jdbc.queryForObject("SELECT activo FROM octano.usuario WHERE id = ?", Boolean.class, usuarioId));
        api.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        api.perform(patch("/api/usuarios/{id}/estado", usuarioId).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}")).andExpect(status().isBadRequest());
        // Reactivar el mecánico no reactiva automáticamente su cuenta.
        estado(id, true);
        assertFalse(jdbc.queryForObject("SELECT activo FROM octano.usuario WHERE id = ?", Boolean.class, usuarioId));
        api.perform(patch("/api/usuarios/{id}/estado", usuarioId).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}")).andExpect(status().isOk());
        api.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        api.perform(get("/api/auth/me").header("Authorization", login("vinculado@mecanicos.test"))).andExpect(status().isOk());
    }
}
