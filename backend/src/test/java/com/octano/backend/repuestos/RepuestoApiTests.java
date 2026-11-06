package com.octano.backend.repuestos;

import com.octano.backend.soporte.ApiFixture;
import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RepuestoApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = new JsonMapper();
    private ApiFixture.Cuenta admin;
    private long condicion;
    private static final String DATOS = """
            {"codigoOem":" ph-123 ","descripcion":" Filtro de aceite ","marca":" Marca de prueba ",
             "precioCosto":100.10,"margen":25.00,"stockMinimo":2.000}
            """;

    @BeforeEach void preparar() throws Exception {
        condicion = jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva", Long.class);
        admin = ApiFixture.registrar(api, "admin@repuestos.test", condicion);
    }
    private long crear(String ruta, String body) throws Exception {
        String respuesta = api.perform(post(ruta).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }
    private long repuesto() throws Exception { return crear("/api/repuestos", DATOS); }
    private String ruta(long id) { return "/api/repuestos/" + id; }
    private String movimiento(String tipo, String cantidad) {
        return "{\"tipo\":\"" + tipo + "\",\"cantidad\":" + cantidad + ",\"motivo\":\"Prueba de stock\"}";
    }
    private long mover(long id, String tipo, String cantidad) throws Exception {
        return crear(ruta(id) + "/movimientos", movimiento(tipo, cantidad));
    }
    private void saldo(long id, String esperado) {
        BigDecimal actual = jdbc.queryForObject("SELECT stock FROM octano.repuesto WHERE id = ?", BigDecimal.class, id);
        assertEquals(0, new BigDecimal(esperado).compareTo(actual));
    }
    private long modelo() throws Exception {
        long marca = ApiFixture.marca(jdbc, "Marca de prueba");
        return ApiFixture.modelo(jdbc, marca, "Modelo de prueba");
    }

    @Test void creaEditaYCalculaElPrecioSinCambiarElStockDirectamente() throws Exception {
        // El precio usa costo y margen; editar los datos no reemplaza las existencias.
        long id = repuesto();
        api.perform(get(ruta(id)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigoOem").value("PH-123"))
                .andExpect(jsonPath("$.precioPublico").value(125.13)).andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.usuarioModificacionId").value(admin.usuarioId()));
        mover(id, "ENTRADA", "5");
        api.perform(put(ruta(id)).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(DATOS.replace("100.10", "0.10").replace("25.00", "5.00").replace("2.000}", "2.000,\"stock\":999}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precioPublico").value(0.11))
                .andExpect(jsonPath("$.stock").value(5));
        saldo(id, "5");
    }

    @Test void buscaPorCodigoDescripcionMarcaYStockBajoConPaginacion() throws Exception {
        // Buscamos texto literal y distinguimos los repuestos que llegaron al mínimo.
        long id = repuesto();
        crear("/api/repuestos", DATOS.replace("ph-123", "otro-456").replace("Filtro de aceite", "Bujia"));
        mover(id, "ENTRADA", "5");
        for (String q : new String[]{"PH-123", "ACEITE"}) {
            api.perform(get("/api/repuestos").param("q", q).header("Authorization", admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        }
        api.perform(get("/api/repuestos").param("q", "marca de prueba").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
        api.perform(get("/api/repuestos?stockBajo=true").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        api.perform(get("/api/repuestos?stockBajo=false").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(id));
        api.perform(get("/api/repuestos?pagina=1&tamano=1").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        for (String q : new String[]{"%", "' OR 1=1 --"}) {
            api.perform(get("/api/repuestos").param("q", q).header("Authorization", admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        }
    }

    @Test void asignaCompatibilidadesGeneralesODeUnaVersionYPermiteQuitarlas() throws Exception {
        // Una compatibilidad general sirve para todas las versiones del modelo.
        long modelo = modelo();
        long version = ApiFixture.version(jdbc, modelo, "Version uno");
        long otraVersion = ApiFixture.version(jdbc, modelo, "Version dos");
        long general = repuesto();
        long especifico = crear("/api/repuestos", DATOS.replace("ph-123", "especifico"));
        long compatibilidad = crear(ruta(general) + "/compatibilidades", "{\"modeloId\":" + modelo + ",\"versionId\":null}");
        crear(ruta(especifico) + "/compatibilidades", "{\"modeloId\":" + modelo + ",\"versionId\":" + version + "}");
        api.perform(get("/api/repuestos").param("versionId", Long.toString(version)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
        api.perform(get("/api/repuestos").param("versionId", Long.toString(otraVersion)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(general))
                .andExpect(jsonPath("$.totalElementos").value(1));
        api.perform(get(ruta(general) + "/compatibilidades").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].versionId").isEmpty());
        // No se puede quitar una compatibilidad pasando el ID de otro repuesto.
        api.perform(delete(ruta(especifico) + "/compatibilidades/" + compatibilidad).header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
        api.perform(delete(ruta(general) + "/compatibilidades/" + compatibilidad).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());
        api.perform(get("/api/repuestos").param("versionId", Long.toString(otraVersion)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test void rechazaUnaVersionQueNoPerteneceAlModelo() throws Exception {
        // El catálogo y la compatibilidad deben apuntar al mismo modelo.
        long id = repuesto();
        long modelo = modelo();
        long marca = jdbc.queryForObject("SELECT marca_id FROM octano.modelo WHERE id = ?", Long.class, modelo);
        long otroModelo = ApiFixture.modelo(jdbc, marca, "Otro modelo");
        long version = ApiFixture.version(jdbc, otroModelo, "Otra version");
        api.perform(post(ruta(id) + "/compatibilidades").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modeloId\":" + modelo + ",\"versionId\":" + version + "}")).andExpect(status().isBadRequest());
        api.perform(get("/api/repuestos").param("modeloId", Long.toString(modelo)).param("versionId", Long.toString(version))
                .header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
        api.perform(post(ruta(id) + "/compatibilidades").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modeloId\":999999}")).andExpect(status().isNotFound());
    }

    @Test void noRepiteUnaCompatibilidadGeneral() throws Exception {
        // También evitamos duplicados cuando no se indicó una versión concreta.
        long id = repuesto();
        long modelo = modelo();
        String body = "{\"modeloId\":" + modelo + "}";
        crear(ruta(id) + "/compatibilidades", body);
        api.perform(post(ruta(id) + "/compatibilidades").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isConflict());
    }

    @Test void elCodigoEsUnicoPorTallerSinDistinguirMayusculas() throws Exception {
        // Otro taller puede usar el mismo código; dentro de uno solo se registra una vez.
        repuesto();
        var otro = ApiFixture.registrar(api, "otro@repuestos.test", condicion);
        api.perform(post("/api/repuestos").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(DATOS)).andExpect(status().isCreated());
        api.perform(post("/api/repuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(DATOS.replace("ph-123", "PH-123"))).andExpect(status().isConflict());
    }

    @Test void registraEntradasSalidasYAjustesConCantidadesDecimales() throws Exception {
        // Se conservan cantidades exactas y el ajuste indica el saldo final contado.
        long id = repuesto();
        long entrada = mover(id, "ENTRADA", "10.250");
        mover(id, "SALIDA", "0.125");
        saldo(id, "10.125");
        mover(id, "AJUSTE", "1.500");
        saldo(id, "1.500");
        mover(id, "AJUSTE", "0");
        saldo(id, "0");
        api.perform(get(ruta(id) + "/movimientos/" + entrada).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockAnterior").value(0))
                .andExpect(jsonPath("$.stockPosterior").value(10.25)).andExpect(jsonPath("$.usuarioId").value(admin.usuarioId()))
                .andExpect(jsonPath("$.fecha").exists());
        api.perform(get(ruta(id) + "/movimientos?tamano=2").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(4))
                .andExpect(jsonPath("$.totalPaginas").value(2)).andExpect(jsonPath("$.contenido[0].variacion").value(-1.5));
    }

    @Test void rechazaSalidasSinSaldoYMovimientosInvalidosSinCambiarNada() throws Exception {
        // Una salida rechazada no descuenta stock ni agrega una fila al historial.
        long id = repuesto();
        mover(id, "ENTRADA", "5");
        for (String body : new String[]{movimiento("SALIDA", "6"), movimiento("ENTRADA", "0"), movimiento("SALIDA", "-1"),
                movimiento("AJUSTE", "5"), movimiento("ENTRADA", "0.0001"), movimiento("OTRO", "1"),
                movimiento("ENTRADA", "1").replace("Prueba de stock", " "), "{}"}) {
            api.perform(post(ruta(id) + "/movimientos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        saldo(id, "5");
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM octano.movimiento_stock WHERE repuesto_id = ?", Long.class, id).longValue());
        // También controlamos el límite del saldo acumulado antes de escribir en la base.
        api.perform(post(ruta(id) + "/movimientos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(movimiento("ENTRADA", "999999999999.999"))).andExpect(status().isBadRequest());
        saldo(id, "5");
    }

    @Test void validaLosDatosDelRepuestoYLaPaginacion() throws Exception {
        // No aceptamos campos vacíos, importes negativos ni más decimales de los admitidos.
        for (String body : new String[]{"{}", DATOS.replace("ph-123", " "), DATOS.replace("Filtro de aceite", " "),
                DATOS.replace("Marca de prueba", " "), DATOS.replace("100.10", "-1"), DATOS.replace("25.00", "-1"),
                DATOS.replace("100.10", "1.001"), DATOS.replace("2.000", "-1"), DATOS.replace("2.000", "0.0001")}) {
            api.perform(post("/api/repuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        for (String filtro : new String[]{"pagina=-1", "tamano=101", "modeloId=0", "stockBajo=otro"}) {
            api.perform(get("/api/repuestos?" + filtro).header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
        }
    }

    @Test void postgresTambienImpideGuardarStockNegativo() throws Exception {
        // La base protege el saldo aunque una consulta intente saltarse las validaciones del servicio.
        long id = repuesto();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE octano.repuesto SET stock = -1 WHERE id = ?", id));
    }

    @Test void conservaElHistorialAlDesactivarYBloqueaNuevosMovimientos() throws Exception {
        // La baja lógica no borra el saldo ni las operaciones anteriores.
        long id = repuesto();
        mover(id, "ENTRADA", "3");
        api.perform(patch(ruta(id) + "/estado").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isOk());
        api.perform(get("/api/repuestos?activo=false").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(id));
        api.perform(post(ruta(id) + "/movimientos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(movimiento("SALIDA", "1"))).andExpect(status().isBadRequest());
        api.perform(get(ruta(id) + "/movimientos").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        api.perform(patch(ruta(id) + "/estado").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":true}")).andExpect(status().isOk());
        mover(id, "SALIDA", "1");
        saldo(id, "2");
    }

    @Test void otroTallerNoPuedeLeerNiModificarRepuestosCompatibilidadesOMovimientos() throws Exception {
        // Conocer IDs ajenos no permite acceder a datos ni descontar existencias.
        long id = repuesto();
        long movimiento = mover(id, "ENTRADA", "3");
        long modelo = modelo();
        long compatibilidad = crear(ruta(id) + "/compatibilidades", "{\"modeloId\":" + modelo + "}");
        var otro = ApiFixture.registrar(api, "otro@repuestos.test", condicion);
        api.perform(get("/api/repuestos").header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        for (String ruta : new String[]{ruta(id), ruta(id) + "/compatibilidades", ruta(id) + "/movimientos", ruta(id) + "/movimientos/" + movimiento}) {
            api.perform(get(ruta).header("Authorization", otro.bearer())).andExpect(status().isNotFound());
        }
        api.perform(put(ruta(id)).header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON).content(DATOS)).andExpect(status().isNotFound());
        api.perform(patch(ruta(id) + "/estado").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isNotFound());
        api.perform(post(ruta(id) + "/movimientos").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(movimiento("SALIDA", "1"))).andExpect(status().isNotFound());
        api.perform(post(ruta(id) + "/compatibilidades").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modeloId\":" + modelo + "}")).andExpect(status().isNotFound());
        api.perform(delete(ruta(id) + "/compatibilidades/" + compatibilidad).header("Authorization", otro.bearer())).andExpect(status().isNotFound());
        saldo(id, "3");
    }

    @Test void recepcionConsultaPeroSoloAdminModificaElInventario() throws Exception {
        // Recepción consulta saldo e historial; el administrador registra los cambios.
        long id = repuesto();
        api.perform(post("/api/usuarios").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"recepcion@repuestos.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"RECEPCION\"}"))
                .andExpect(status().isCreated());
        String respuesta = api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"recepcion@repuestos.test\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String recepcion = "Bearer " + json.readTree(respuesta).get("tokenAcceso").asString();
        api.perform(get("/api/repuestos")).andExpect(status().isUnauthorized());
        for (String ruta : new String[]{"/api/repuestos", ruta(id), ruta(id) + "/movimientos", ruta(id) + "/compatibilidades"}) {
            api.perform(get(ruta).header("Authorization", recepcion)).andExpect(status().isOk());
        }
        api.perform(post("/api/repuestos").header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON).content(DATOS)).andExpect(status().isForbidden());
        api.perform(put(ruta(id)).header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON).content(DATOS)).andExpect(status().isForbidden());
        api.perform(post(ruta(id) + "/movimientos").header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON)
                .content(movimiento("ENTRADA", "1"))).andExpect(status().isForbidden());
        api.perform(patch(ruta(id) + "/estado").header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isForbidden());
        api.perform(post(ruta(id) + "/compatibilidades").header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modeloId\":1}")).andExpect(status().isForbidden());
    }

    @Test void mecanicoConsultaStockCompatibilidadesYMovimientosSinPoderModificarlos() throws Exception {
        // El mecánico puede leer el inventario de su taller, pero no registrar cambios manuales.
        long id = repuesto();
        long entrada = mover(id, "ENTRADA", "5");
        long modelo = modelo();
        long compatibilidad = crear(ruta(id) + "/compatibilidades", "{\"modeloId\":" + modelo + "}");
        long mecanico = crear("/api/mecanicos", "{\"nombre\":\"Juan\",\"apellido\":\"Perez\",\"telefono\":\"02392 456789\",\"fechaInicio\":\"2020-01-01\"}");
        crear("/api/usuarios", "{\"email\":\"mecanico@stock.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"MECANICO\",\"mecanicoId\":" + mecanico + "}");
        String respuesta = api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"mecanico@stock.test\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(respuesta).get("tokenAcceso").asString();
        for (String ruta : new String[]{"/api/repuestos", ruta(id), ruta(id) + "/compatibilidades", ruta(id) + "/movimientos", ruta(id) + "/movimientos/" + entrada}) {
            api.perform(get(ruta).header("Authorization", token)).andExpect(status().isOk());
        }
        api.perform(get(ruta(id)).header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(5));
        api.perform(post("/api/repuestos").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isForbidden());
        api.perform(put(ruta(id)).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isForbidden());
        api.perform(patch(ruta(id) + "/estado").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isForbidden());
        api.perform(post(ruta(id) + "/movimientos").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(movimiento("SALIDA", "1"))).andExpect(status().isForbidden());
        api.perform(post(ruta(id) + "/compatibilidades").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modeloId\":" + modelo + "}")).andExpect(status().isForbidden());
        api.perform(delete(ruta(id) + "/compatibilidades/" + compatibilidad).header("Authorization", token))
                .andExpect(status().isForbidden());
        saldo(id, "5");
        // La apertura de permisos de lectura conserva el aislamiento entre talleres.
        var otro = ApiFixture.registrar(api, "otro@stock.test", condicion);
        String ajeno = api.perform(post("/api/repuestos").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(DATOS)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idAjeno = json.readTree(ajeno).get("id").asLong();
        api.perform(get(ruta(idAjeno)).header("Authorization", token)).andExpect(status().isNotFound());
        api.perform(get(ruta(idAjeno) + "/movimientos").header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test void compatibilidadesIncluyenMarcaModeloYVersionEnAltaYConsulta() throws Exception {
        // Devolvemos los nombres junto a los IDs para que el frontend pueda mostrar la compatibilidad.
        long id = repuesto();
        long modelo = modelo();
        long marca = jdbc.queryForObject("SELECT marca_id FROM octano.modelo WHERE id = ?", Long.class, modelo);
        long version = ApiFixture.version(jdbc, modelo, "Version de prueba");
        api.perform(post(ruta(id) + "/compatibilidades").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modeloId\":" + modelo + ",\"versionId\":" + version + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.marcaId").value(marca))
                .andExpect(jsonPath("$.marca").value("Marca de prueba")).andExpect(jsonPath("$.modelo").value("Modelo de prueba"))
                .andExpect(jsonPath("$.version").value("Version de prueba"));
        api.perform(post(ruta(id) + "/compatibilidades").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modeloId\":" + modelo + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.marca").value("Marca de prueba"))
                .andExpect(jsonPath("$.modelo").value("Modelo de prueba")).andExpect(jsonPath("$.version").isEmpty());
        api.perform(get(ruta(id) + "/compatibilidades").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].marca").value("Marca de prueba"))
                .andExpect(jsonPath("$[0].modelo").value("Modelo de prueba")).andExpect(jsonPath("$[0].version").value("Version de prueba"));
    }

    @Test void listadoMuestraActivosPorDefectoYPermitePedirInactivos() throws Exception {
        // La baja desaparece del listado habitual y sigue disponible con el filtro explícito.
        long activo = repuesto();
        long inactivo = crear("/api/repuestos", DATOS.replace("ph-123", "baja-001"));
        api.perform(patch(ruta(inactivo) + "/estado").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isOk());
        api.perform(get("/api/repuestos").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(activo));
        api.perform(get("/api/repuestos?activo=false").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(inactivo));
    }
}
