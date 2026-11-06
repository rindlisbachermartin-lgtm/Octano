package com.octano.backend.vehiculos;

import com.octano.backend.soporte.ApiFixture;
import java.time.Year;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VehiculoApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = new JsonMapper();
    private ApiFixture.Cuenta admin;
    private long condicion, cliente, marca, modelo, version;

    @BeforeEach void preparar() throws Exception {
        condicion = jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva", Long.class);
        admin = ApiFixture.registrar(api, "admin@vehiculos.test", condicion);
        cliente = cliente("12345678");
        marca = ApiFixture.marca(jdbc, "Marca de prueba");
        modelo = ApiFixture.modelo(jdbc, marca, "Modelo de prueba");
        version = ApiFixture.version(jdbc, modelo, "Version de prueba");
    }
    private long crear(String ruta, String body) throws Exception {
        String respuesta = api.perform(post(ruta).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }
    private long cliente(String documento) throws Exception {
        return crear("/api/clientes", """
                {"nombre":"Cliente de prueba","documento":"%s","telefono":"02392 456789","condicionIvaId":%d}
                """.formatted(documento, condicion));
    }
    private String datos(String patente) {
        return """
                {"clienteId":%d,"modeloId":%d,"versionId":%d,"patente":"%s","anio":2020,"kilometraje":10000}
                """.formatted(cliente, modelo, version, patente);
    }
    private String login(String email) throws Exception {
        String respuesta = api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(respuesta).get("tokenAcceso").asString();
    }

    @Test void elCatalogoRespetaLaRelacionMarcaModeloVersion() throws Exception {
        // Cada selector devuelve solamente las opciones de su marca o modelo.
        api.perform(get("/api/catalogo/marcas").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Marca de prueba"));
        api.perform(get("/api/catalogo/modelos").param("marcaId", Long.toString(marca)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].padreId").value(marca));
        api.perform(get("/api/catalogo/versiones").param("modeloId", Long.toString(modelo)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(version));
        long otraMarca = ApiFixture.marca(jdbc, "Otra marca");
        api.perform(get("/api/catalogo/modelos").param("marcaId", Long.toString(otraMarca)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        api.perform(get("/api/catalogo/modelos?marcaId=999999").header("Authorization", admin.bearer())).andExpect(status().isNotFound());
    }

    @Test void elCatalogoCompartidoEsSoloLecturaInclusoParaAdmin() throws Exception {
        // Un administrador de taller tampoco puede agregar opciones visibles para todos.
        for (String ruta : new String[]{"marcas", "modelos", "versiones"}) {
            api.perform(post("/api/catalogo/" + ruta).header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isMethodNotAllowed());
        }
    }

    @Test void creaConsultaEditaYBuscaUnVehiculo() throws Exception {
        // Normalizamos la patente y guardamos el usuario que hizo la modificación.
        long id = crear("/api/vehiculos", datos("ab123cd"));
        api.perform(get("/api/vehiculos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patente").value("AB-123-CD"))
                .andExpect(jsonPath("$.usuarioModificacionId").value(admin.usuarioId()));
        api.perform(put("/api/vehiculos/{id}", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("AB-123-CD").replace("10000", "15000")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kilometraje").value(15000));
        assertEquals(15000, jdbc.queryForObject("SELECT kilometraje FROM octano.vehiculo WHERE id = ?", Integer.class, id));
        api.perform(get("/api/vehiculos").param("patente", "ab-123").param("clienteId", Long.toString(cliente))
                .header("Authorization", admin.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].id").value(id));
        crear("/api/vehiculos", datos("xyz987"));
        api.perform(get("/api/vehiculos?pagina=1&tamano=1").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        api.perform(get("/api/vehiculos").param("patente", "%").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test void rechazaDatosInvalidosYVersionesDeOtroModelo() throws Exception {
        // No aceptamos patentes inválidas, kilometrajes negativos ni años fuera del rango.
        for (String body : new String[]{"{}", datos("invalida"), datos("abc123").replace("10000", "-1"),
                datos("abc123").replace("2020", "1899"), datos("abc123").replace("2020", Integer.toString(Year.now().getValue() + 2)),
                datos("abc123").replace("10000", "1.5")}) {
            api.perform(post("/api/vehiculos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        long otroModelo = ApiFixture.modelo(jdbc, marca, "Otro modelo");
        api.perform(post("/api/vehiculos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("abc123").replace("\"modeloId\":" + modelo, "\"modeloId\":" + otroModelo)))
                .andExpect(status().isBadRequest());
        // Podemos registrar un vehículo si todavía no conocemos su versión.
        crear("/api/vehiculos", datos("abc123").replace("\"versionId\":" + version, "\"versionId\":null"));
        for (String filtro : new String[]{"pagina=-1", "tamano=101", "clienteId=0"}) {
            api.perform(get("/api/vehiculos?" + filtro).header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
        }
    }

    @Test void conservaElHistorialDelTitularEnAltasEdicionesYCambios() throws Exception {
        // El alta registra el primer titular y cada cambio guarda al anterior y al nuevo.
        long id = crear("/api/vehiculos", datos("abc123"));
        long otroCliente = cliente("23456789");
        api.perform(patch("/api/vehiculos/{id}/titular", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":" + otroCliente + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(otroCliente));
        // Repetir el mismo titular se rechaza y no agrega entradas duplicadas.
        api.perform(patch("/api/vehiculos/{id}/titular", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":" + otroCliente + "}")).andExpect(status().isBadRequest());
        api.perform(put("/api/vehiculos/{id}", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("abc123"))).andExpect(status().isOk());
        api.perform(get("/api/vehiculos/{id}/titularidad", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].clienteAnteriorId").isEmpty()).andExpect(jsonPath("$[0].clienteNuevoId").value(cliente))
                .andExpect(jsonPath("$[1].clienteAnteriorId").value(cliente)).andExpect(jsonPath("$[1].clienteNuevoId").value(otroCliente))
                .andExpect(jsonPath("$[2].clienteAnteriorId").value(otroCliente)).andExpect(jsonPath("$[2].usuarioId").value(admin.usuarioId()));
        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM octano.vehiculo WHERE id = ?", Long.class, id).longValue());
    }

    @Test void laBajaConservaElVehiculoYNoPermiteAsignarUnClienteInactivo() throws Exception {
        // Las bajas son lógicas y los clientes inactivos no reciben vehículos nuevos.
        long id = crear("/api/vehiculos", datos("abc123"));
        api.perform(patch("/api/vehiculos/{id}/estado", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        api.perform(get("/api/vehiculos?activo=false").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(id));
        api.perform(patch("/api/vehiculos/{id}/estado", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}")).andExpect(status().isOk());
        api.perform(patch("/api/clientes/{id}/estado", cliente).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());
        api.perform(post("/api/vehiculos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("xyz987"))).andExpect(status().isBadRequest());
        // Los datos del vehículo existente se pueden corregir sin cambiar su titular inactivo.
        api.perform(put("/api/vehiculos/{id}", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("abc123"))).andExpect(status().isOk());
    }

    @Test void aislaVehiculosClientesEHistorialEntreTalleres() throws Exception {
        // Otro taller no puede leer el vehículo, cambiarlo ni usar a nuestro cliente.
        long id = crear("/api/vehiculos", datos("abc123"));
        var otro = ApiFixture.registrar(api, "otro@vehiculos.test", condicion);
        api.perform(get("/api/vehiculos").header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        for (String ruta : new String[]{"/api/vehiculos/" + id, "/api/vehiculos/" + id + "/titularidad"}) {
            api.perform(get(ruta).header("Authorization", otro.bearer())).andExpect(status().isNotFound());
        }
        api.perform(post("/api/vehiculos").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("xyz987"))).andExpect(status().isNotFound());
        api.perform(put("/api/vehiculos/{id}", id).header("Authorization", otro.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("abc123"))).andExpect(status().isNotFound());
        api.perform(patch("/api/vehiculos/{id}/titular", id).header("Authorization", otro.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":" + cliente + "}")).andExpect(status().isNotFound());
        api.perform(patch("/api/vehiculos/{id}/estado", id).header("Authorization", otro.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isNotFound());
    }

    @Test void laPatenteNormalizadaNoPuedeRepetirseEnElTaller() throws Exception {
        // Agregar espacios o guiones no permite duplicar una patente ya registrada.
        crear("/api/vehiculos", datos("abc123"));
        api.perform(post("/api/vehiculos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("ABC-123"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PATENTE_DUPLICADA"))
                .andExpect(jsonPath("$.campo").value("patente"))
                .andExpect(jsonPath("$.detail").value("Ya existe un vehículo con esa patente en tu taller."));
    }

    @Test void recepcionGestionaVehiculosYConsultaElCatalogoSinModificarlo() throws Exception {
        // Sin sesión no hay acceso; Recepción consulta el catálogo y registra vehículos.
        api.perform(get("/api/vehiculos")).andExpect(status().isUnauthorized());
        api.perform(get("/api/catalogo/marcas")).andExpect(status().isUnauthorized());
        api.perform(post("/api/usuarios").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"recepcion@vehiculos.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"RECEPCION\"}"))
                .andExpect(status().isCreated());
        String recepcion = login("recepcion@vehiculos.test");
        api.perform(get("/api/catalogo/marcas").header("Authorization", recepcion)).andExpect(status().isOk());
        api.perform(post("/api/catalogo/marcas").header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"No permitida\"}")).andExpect(status().isMethodNotAllowed());
        api.perform(post("/api/vehiculos").header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON)
                .content(datos("abc123"))).andExpect(status().isCreated());
        long recepcionId = jdbc.queryForObject("SELECT id FROM octano.usuario WHERE email = 'recepcion@vehiculos.test'", Long.class);
        assertEquals(recepcionId, jdbc.queryForObject("SELECT usuario_modificacion_id FROM octano.vehiculo WHERE taller_id = ?", Long.class, admin.tallerId()).longValue());
    }

    @Test void buscaComoElFrontendPorClienteMarcaModeloYPatente() throws Exception {
        // Ignoramos acentos y separadores, y buscamos palabras en distintos campos.
        jdbc.update("UPDATE octano.cliente SET nombre = 'José Pérez' WHERE id = ?", cliente);
        jdbc.update("UPDATE octano.marca SET nombre = 'Citroën' WHERE id = ?", marca);
        jdbc.update("UPDATE octano.modelo SET nombre = 'C4 Cactus' WHERE id = ?", modelo);
        long id = crear("/api/vehiculos", datos("ab123cd"));
        for (String q : new String[]{"PEREZ", "José", "CITROEN", "C4Cactus", "AB 123 CD", "perez c4", "citroën jose"}) {
            api.perform(get("/api/vehiculos").param("q", q).header("Authorization", admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].id").value(id));
        }
        for (String q : new String[]{"perez inexistente", "%", "' OR 1=1 --"}) {
            api.perform(get("/api/vehiculos").param("q", q).header("Authorization", admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        }
        var otro = ApiFixture.registrar(api, "otro@vehiculos.test", condicion);
        api.perform(get("/api/vehiculos").param("q", "perez").header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test void devuelveLosNombresNecesariosParaMostrarElVehiculo() throws Exception {
        // El alta, la consulta y el listado incluyen nombres, sin obligar al frontend a resolver los IDs.
        String respuesta = api.perform(post("/api/vehiculos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("abc123"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.marcaId").value(marca)).andExpect(jsonPath("$.marca").value("Marca de prueba"))
                .andExpect(jsonPath("$.modelo").value("Modelo de prueba")).andExpect(jsonPath("$.version").value("Version de prueba"))
                .andExpect(jsonPath("$.clienteNombre").value("Cliente de prueba"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        api.perform(get("/api/vehiculos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marca").value("Marca de prueba"));
        api.perform(get("/api/vehiculos").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].modelo").value("Modelo de prueba"));
    }

    @Test void kilometrajeEsOpcionalYDistingueDesconocidoDeCero() throws Exception {
        // Omitir el kilometraje guarda NULL; indicar cero guarda el número cero.
        long sinDato = crear("/api/vehiculos", datos("abc123").replace(",\"kilometraje\":10000", ""));
        long nulo = crear("/api/vehiculos", datos("xyz987").replace("10000", "null"));
        long cero = crear("/api/vehiculos", datos("ab123cd").replace("10000", "0"));
        assertNull(jdbc.queryForObject("SELECT kilometraje FROM octano.vehiculo WHERE id = ?", Integer.class, sinDato));
        assertNull(jdbc.queryForObject("SELECT kilometraje FROM octano.vehiculo WHERE id = ?", Integer.class, nulo));
        assertEquals(0, jdbc.queryForObject("SELECT kilometraje FROM octano.vehiculo WHERE id = ?", Integer.class, cero));
        api.perform(get("/api/vehiculos/{id}", sinDato).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kilometraje").isEmpty());
        api.perform(put("/api/vehiculos/{id}", cero).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("ab123cd").replace(",\"kilometraje\":10000", "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kilometraje").isEmpty());
    }

    @Test void noTransfiereUnVehiculoInactivoPorNingunaDeLasDosVias() throws Exception {
        // También cerramos la vía de cambiar el cliente desde el formulario de edición.
        long id = crear("/api/vehiculos", datos("abc123"));
        long nuevoCliente = cliente("23456789");
        jdbc.update("UPDATE octano.cliente SET nombre = 'Ana Gómez' WHERE id = ?", nuevoCliente);
        api.perform(patch("/api/vehiculos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isOk());
        api.perform(patch("/api/vehiculos/{id}/titular", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":" + nuevoCliente + "}")).andExpect(status().isBadRequest());
        api.perform(put("/api/vehiculos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("abc123").replace("\"clienteId\":" + cliente, "\"clienteId\":" + nuevoCliente)))
                .andExpect(status().isBadRequest());
        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM octano.vehiculo WHERE id = ?", Long.class, id).longValue());
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM octano.historial_titularidad WHERE vehiculo_id = ?", Long.class, id).longValue());
        api.perform(patch("/api/vehiculos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":true}")).andExpect(status().isOk());
        api.perform(patch("/api/vehiculos/{id}/titular", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":" + nuevoCliente + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNombre").value("Ana Gómez"));
    }

    @Test void elCatalogoEsCompartidoPeroNingunTallerPuedeCambiarlo() throws Exception {
        // Otro taller ve las opciones generales, pero ni su ADMIN puede publicar nombres nuevos.
        var otro = ApiFixture.registrar(api, "otro@vehiculos.test", condicion);
        api.perform(get("/api/catalogo/modelos").param("marcaId", Long.toString(marca)).header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(modelo));
        for (String ruta : new String[]{"marcas", "modelos", "versiones"}) {
            api.perform(post("/api/catalogo/" + ruta).header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"marcaId\":" + marca + ",\"modeloId\":" + modelo + ",\"nombre\":\"Nombre nuevo\"}"))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM octano.modelo WHERE marca_id = ?", Long.class, marca).longValue());
    }
}
