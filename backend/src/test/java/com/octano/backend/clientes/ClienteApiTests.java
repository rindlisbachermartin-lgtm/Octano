package com.octano.backend.clientes;

import com.octano.backend.soporte.ApiFixture;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClienteApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    private ApiFixture.Cuenta admin;
    private long condicion;
    private final JsonMapper json = new JsonMapper();

    @BeforeEach void preparar() throws Exception {
        condicion = jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva", Long.class);
        admin = ApiFixture.registrar(api, "admin@clientes.test", condicion);
    }

    private String datos(String nombre, String documento) {
        return """
                {"nombre":"%s","documento":"%s","telefono":"02392 456789","email":" CLIENTE@Ejemplo.test ",
                 "calle":" Calle 123 ","altura":100,"condicionIvaId":%d}
                """.formatted(nombre, documento, condicion);
    }
    private long crear(String nombre, String documento) throws Exception {
        String respuesta = api.perform(post("/api/clientes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos(nombre, documento)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.activo").value(true)).andExpect(jsonPath("$.fechaModif").exists())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }
    private void estado(long id, boolean activo) throws Exception {
        api.perform(patch("/api/clientes/{id}/estado", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":" + activo + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(activo));
    }
    private String cuentaRecepcion() throws Exception {
        api.perform(post("/api/usuarios").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"recepcion@clientes.test","password":"ClaveDePrueba123!","rol":"RECEPCION"}
                        """)).andExpect(status().isCreated());
        return login("recepcion@clientes.test");
    }
    private String login(String email) throws Exception {
        String respuesta = api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(respuesta).get("tokenAcceso").asString();
    }

    @Test void creaConsultaYEditaConLosDatosNormalizados() throws Exception {
        // Guardamos el cliente, quitamos espacios y comprobamos que la edición llega a la base.
        long id = crear(" Juan Perez ", "12.345.678");
        api.perform(get("/api/clientes/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Juan Perez"))
                .andExpect(jsonPath("$.documento").value("12345678"))
                .andExpect(jsonPath("$.email").value("cliente@ejemplo.test"));
        api.perform(put("/api/clientes/{id}", id).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("Juan Actualizado", "12345678")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Juan Actualizado"));
        assertEquals("Juan Actualizado", jdbc.queryForObject("SELECT nombre FROM octano.cliente WHERE id = ?", String.class, id));
        assertEquals(admin.usuarioId(), jdbc.queryForObject("SELECT usuario_modificacion_id FROM octano.cliente WHERE id = ?", Long.class, id).longValue());
        assertEquals(admin.tallerId(), jdbc.queryForObject("SELECT taller_id FROM octano.cliente WHERE id = ?", Long.class, id).longValue());
    }

    @Test void buscaPorNombreDocumentoYEmailYPaginaLosResultados() throws Exception {
        // Buscamos sin distinguir mayúsculas y dividimos el listado en páginas.
        crear("Ana", "12345678");
        crear("Zoe", "23456789");
        for (String q : new String[]{"ANA", "12.345.678"}) {
            api.perform(get("/api/clientes").param("q", q).header("Authorization", admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].nombre").value("Ana"));
        }
        api.perform(get("/api/clientes").param("q", "CLIENTE@EJEMPLO").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
        api.perform(get("/api/clientes?pagina=1&tamano=1").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].nombre").value("Zoe"))
                .andExpect(jsonPath("$.totalElementos").value(2)).andExpect(jsonPath("$.totalPaginas").value(2));
        // Los símbolos de búsqueda se toman como texto, no como instrucciones SQL ni comodines.
        for (String q : new String[]{"%", "' OR 1=1 --", "."}) {
            api.perform(get("/api/clientes").param("q", q).header("Authorization", admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(q.equals(".") ? 2 : 0));
        }
    }

    @Test void daDeBajaYReactivaSinBorrarElCliente() throws Exception {
        // La baja conserva los datos y permite consultar clientes inactivos.
        long id = crear("Juan", "12345678");
        estado(id, false);
        api.perform(get("/api/clientes?activo=true").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        api.perform(get("/api/clientes?activo=false").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(id));
        api.perform(get("/api/clientes/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        estado(id, true);
    }

    @Test void rechazaLosDatosInvalidosSinGuardarClientes() throws Exception {
        // Los campos obligatorios, el email, la altura y el CUIT deben ser válidos.
        String base = datos("Juan", "12345678");
        for (String body : new String[]{"{}", datos(" ", "12345678"), datos("Juan", "abc"),
                datos("Juan", "20-12345678-5"), base.replace("02392 456789", "......"),
                base.replace(" CLIENTE@Ejemplo.test ", "no-es-email"), base.replace("\"altura\":100", "\"altura\":-1"),
                base.replace("\"altura\":100", "\"altura\":1.5")}) {
            api.perform(post("/api/clientes").header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        api.perform(post("/api/clientes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(base.replace("\"condicionIvaId\":" + condicion, "\"condicionIvaId\":999999")))
                .andExpect(status().isNotFound());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM octano.cliente WHERE taller_id = ?", Long.class, admin.tallerId()).longValue());
        api.perform(patch("/api/clientes/1/estado").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        for (String parametros : new String[]{"pagina=-1", "tamano=0", "tamano=101", "activo=otro"}) {
            api.perform(get("/api/clientes?" + parametros).header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
        }
    }

    @Test void admiteCuitValidoYCamposOpcionalesVacios() throws Exception {
        // Email y dirección pueden quedar vacíos, y un CUIT válido se guarda sin guiones.
        api.perform(post("/api/clientes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("Empresa SA", "20-12345678-6")
                        .replace(" CLIENTE@Ejemplo.test ", "").replace(" Calle 123 ", "").replace("\"altura\":100", "\"altura\":null")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.documento").value("20123456786"))
                .andExpect(jsonPath("$.email").isEmpty()).andExpect(jsonPath("$.calle").isEmpty()).andExpect(jsonPath("$.altura").isEmpty());
    }

    @Test void elDocumentoEsUnicoDentroDeCadaTaller() throws Exception {
        // El mismo documento puede existir en dos talleres, pero no repetirse dentro de uno.
        crear("Juan", "12345678");
        var otro = ApiFixture.registrar(api, "otro@clientes.test", condicion);
        api.perform(post("/api/clientes").header("Authorization", otro.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("Juan", "12345678"))).andExpect(status().isCreated());
        api.perform(post("/api/clientes").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("Duplicado", "12.345.678")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("La operación entra en conflicto con los datos guardados."));
    }

    @Test void otroTallerNoPuedeConsultarNiModificarElCliente() throws Exception {
        // Conocer un ID ajeno no permite acceder al cliente ni cambiar su taller.
        long id = crear("Juan", "12345678");
        var otro = ApiFixture.registrar(api, "otro@clientes.test", condicion);
        api.perform(get("/api/clientes").header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        api.perform(get("/api/clientes/{id}", id).header("Authorization", otro.bearer())).andExpect(status().isNotFound());
        api.perform(put("/api/clientes/{id}", id).header("Authorization", otro.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(datos("Otro", "12345678"))).andExpect(status().isNotFound());
        api.perform(patch("/api/clientes/{id}/estado", id).header("Authorization", otro.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isNotFound());
    }

    @Test void recepcionPuedeGestionarClientesYQuedaRegistradaComoAutor() throws Exception {
        // Recepción puede atender clientes; registramos su usuario como autor del cambio.
        String recepcion = cuentaRecepcion();
        api.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
        String respuesta = api.perform(post("/api/clientes").header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(datos("Juan", "12345678")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        long recepcionId = jdbc.queryForObject("SELECT id FROM octano.usuario WHERE email = 'recepcion@clientes.test'", Long.class);
        api.perform(get("/api/clientes/{id}", id).header("Authorization", recepcion)).andExpect(status().isOk());
        api.perform(put("/api/clientes/{id}", id).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(datos("Editado", "12345678")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioModificacionId").value(recepcionId));
        api.perform(patch("/api/clientes/{id}/estado", id).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());
        // Un usuario MECANICO no puede acceder a la gestión de clientes.
        long mecanicoId = jdbc.queryForObject("INSERT INTO octano.mecanico(taller_id,nombre,apellido,telefono,fecha_inicio) VALUES (?, 'Juan', 'Perez', '02392 456789', '2020-01-01') RETURNING id", Long.class, admin.tallerId());
        api.perform(post("/api/usuarios").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"mecanico@clientes.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"MECANICO\",\"mecanicoId\":" + mecanicoId + "}"))
                .andExpect(status().isCreated());
        api.perform(get("/api/clientes").header("Authorization", login("mecanico@clientes.test"))).andExpect(status().isForbidden());
    }
}
