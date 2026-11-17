package com.octano.backend.presupuestos;

import com.octano.backend.soporte.ApiFixture;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
class PresupuestoApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = new JsonMapper();
    private ApiFixture.Cuenta admin;
    private long condicion, cliente, vehiculo, modelo, repuesto;
    private static final String REPUESTO = """
            {"codigoOem":"PRES-001","descripcion":"Filtro de prueba","marca":"Prueba",
             "precioCosto":100.10,"margen":25,"stockMinimo":1}
            """;

    @BeforeEach void preparar() throws Exception {
        condicion = jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva", Long.class);
        admin = ApiFixture.registrar(api, "admin@presupuestos.test", condicion);
        cliente = crear("/api/clientes", "{\"nombre\":\"Cliente de prueba\",\"documento\":\"12345678\",\"telefono\":\"02392 456789\",\"condicionIvaId\":" + condicion + "}");
        long marca = ApiFixture.marca(jdbc, "Marca de prueba");
        modelo = ApiFixture.modelo(jdbc, marca, "Modelo de prueba");
        vehiculo = crearVehiculo("abc123");
        repuesto = crear("/api/repuestos", REPUESTO);
    }
    private long crear(String ruta, String body) throws Exception {
        String respuesta = api.perform(post(ruta).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }
    private long crearVehiculo(String patente) throws Exception {
        return crear("/api/vehiculos", "{\"clienteId\":" + cliente + ",\"modeloId\":" + modelo + ",\"patente\":\"" + patente + "\",\"anio\":2020,\"kilometraje\":10000}");
    }
    private String items() {
        return "[{\"repuestoId\":" + repuesto + ",\"cantidad\":2},{\"descripcion\":\"Insumo manual\",\"cantidad\":1.5,\"precioUnitario\":10.10}]";
    }
    private String datos(String items) {
        return """
                {"clienteId":%d,"vehiculoId":%d,"descripcion":" Servicio de prueba ","manoObra":50.00,
                 "porcentajeIva":21,"tiposTrabajo":["SERVICE"],"items":%s}
                """.formatted(cliente, vehiculo, items);
    }
    private void estado(long id, String estado) throws Exception {
        api.perform(patch("/api/presupuestos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado));
    }
    private String turno(long presupuesto, long vehiculoId, LocalDate fecha) {
        return "{\"clienteId\":" + cliente + ",\"vehiculoId\":" + vehiculoId + ",\"presupuestoId\":" + presupuesto + ",\"fecha\":\"" + fecha + "\",\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\",\"motivoVisita\":\"Servicio\"}";
    }
    private LocalDate manana() { return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1); }
    private void sinConsumo() {
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT stock FROM octano.repuesto WHERE id = ?", BigDecimal.class, repuesto)));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM octano.movimiento_stock WHERE repuesto_id = ?", Long.class, repuesto).longValue());
    }

    @Test void calculaLosTotalesYConservaLosPreciosDelPresupuesto() throws Exception {
        // Usamos precios del catálogo y renglones manuales; el backend calcula todos los importes.
        String respuesta = api.perform(post("/api/presupuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(items()))).andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.descripcion").value("Servicio de prueba"))
                .andExpect(jsonPath("$.totalRepuestos").value(265.41)).andExpect(jsonPath("$.subtotal").value(315.41))
                .andExpect(jsonPath("$.montoIva").value(66.24)).andExpect(jsonPath("$.total").value(381.65))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(125.13))
                .andExpect(jsonPath("$.usuarioModificacionId").value(admin.usuarioId()))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        // Cambiar el catálogo no modifica un presupuesto que ya estaba guardado.
        api.perform(put("/api/repuestos/{id}", repuesto).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(REPUESTO.replace("100.10", "200.00").replace("Filtro de prueba", "Nombre nuevo"))).andExpect(status().isOk());
        api.perform(get("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(381.65))
                .andExpect(jsonPath("$.items[0].descripcion").value("Filtro de prueba"));
        sinConsumo();
    }

    @Test void admiteSoloManoDeObraYUsaElIvaConfigurado() throws Exception {
        // Un presupuesto puede tener solo mano de obra y usar IVA cero o el valor por defecto.
        long id = crear("/api/presupuestos", datos("[]").replace("\"porcentajeIva\":21,", ""));
        api.perform(get("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(60.5));
        api.perform(post("/api/presupuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[]").replace("\"porcentajeIva\":21", "\"porcentajeIva\":0")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.montoIva").value(0)).andExpect(jsonPath("$.total").value(50));
    }

    @Test void redondeaCadaRenglonYNoConfiaEnTotalesEnviadosPorElCliente() throws Exception {
        // Dos importes de medio centavo se redondean individualmente a un centavo cada uno.
        String items = "[{\"repuestoId\":" + repuesto + ",\"cantidad\":0.05,\"precioUnitario\":0.10},{\"descripcion\":\"Manual\",\"cantidad\":0.05,\"precioUnitario\":0.10}]";
        api.perform(post("/api/presupuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(items).replace("50.00", "0.00").replace("\"manoObra\":", "\"total\":999,\"subtotal\":999,\"montoIva\":999,\"manoObra\":")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalRepuestos").value(0.02))
                .andExpect(jsonPath("$.items[0].subtotal").value(0.01)).andExpect(jsonPath("$.total").value(0.02));
    }

    @Test void editaPendientesYUnErrorNoBorraLosDetallesAnteriores() throws Exception {
        // La cabecera y los renglones se reemplazan juntos; un cambio inválido conserva lo anterior.
        long id = crear("/api/presupuestos", datos(items()));
        api.perform(put("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[]").replace("Servicio de prueba", "Solo mano de obra"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.total").value(60.5));
        api.perform(put("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[{\"repuestoId\":999999,\"cantidad\":1}]"))).andExpect(status().isNotFound());
        api.perform(get("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Solo mano de obra"))
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.total").value(60.5));
    }

    @Test void aceptarFijaLosImportesYNoDescuentaStock() throws Exception {
        // Un presupuesto aceptado queda protegido frente a ediciones y puede anularse conservando sus datos.
        long id = crear("/api/presupuestos", datos(items()));
        estado(id, "ACEPTADO");
        api.perform(put("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[]"))).andExpect(status().isBadRequest());
        api.perform(patch("/api/presupuestos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RECHAZADO\"}")).andExpect(status().isBadRequest());
        estado(id, "ANULADO");
        api.perform(patch("/api/presupuestos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"PENDIENTE\"}")).andExpect(status().isBadRequest());
        api.perform(get("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.estadoUsuarioId").value(admin.usuarioId())).andExpect(jsonPath("$.total").value(381.65));
        sinConsumo();
    }

    @Test void validaImportesCantidadesRenglonesYTiposDeTrabajo() throws Exception {
        // Rechazamos números inválidos y renglones manuales incompletos antes de guardar.
        for (String body : new String[]{"{}", datos(items()).replace("Servicio de prueba", " "), datos(items()).replace("50.00", "-1"),
                datos(items()).replace("50.00", "0.001"), datos(items()).replace("\"porcentajeIva\":21", "\"porcentajeIva\":101"),
                datos(items()).replace("[\"SERVICE\"]", "[]"), datos(items()).replace("[\"SERVICE\"]", "[\"OTRO_TIPO\"]"),
                datos("[null]"), datos("[{\"cantidad\":1}]"), datos("[{\"cantidad\":0,\"precioUnitario\":10,\"descripcion\":\"Manual\"}]"),
                datos("[{\"cantidad\":0.0001,\"precioUnitario\":10,\"descripcion\":\"Manual\"}]"),
                datos("[{\"cantidad\":1,\"precioUnitario\":-1,\"descripcion\":\"Manual\"}]"),
                datos("[{\"cantidad\":999999999999.999,\"precioUnitario\":999999999999.99,\"descripcion\":\"Manual\"}]"),
                datos("[{\"repuestoId\":" + repuesto + ",\"cantidad\":1},{\"repuestoId\":" + repuesto + ",\"cantidad\":2}]")}) {
            api.perform(post("/api/presupuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM octano.presupuesto WHERE taller_id = ?", Long.class, admin.tallerId()).longValue());
    }

    @Test void exigeTitularCorrectoYDatosActivos() throws Exception {
        // El cliente debe ser titular del vehículo y los datos utilizados deben estar activos.
        long otroCliente = crear("/api/clientes", "{\"nombre\":\"Otro cliente\",\"documento\":\"23456789\",\"telefono\":\"02392 456789\",\"condicionIvaId\":" + condicion + "}");
        api.perform(post("/api/presupuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(items()).replace("\"clienteId\":" + cliente, "\"clienteId\":" + otroCliente))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE octano.repuesto SET activo = FALSE WHERE id = ?", repuesto);
        api.perform(post("/api/presupuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(items()))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE octano.vehiculo SET activo = FALSE WHERE id = ?", vehiculo);
        api.perform(post("/api/presupuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[]"))).andExpect(status().isBadRequest());
    }

    @Test void buscaPorDescripcionClientePatenteEstadoYFecha() throws Exception {
        // El listado devuelve resúmenes y permite filtrar y paginar los presupuestos propios.
        long id = crear("/api/presupuestos", datos(items()));
        crear("/api/presupuestos", datos("[]").replace("Servicio de prueba", "Otro trabajo"));
        estado(id, "ACEPTADO");
        for (String q : new String[]{"SERVICIO", "ABc-123", "cliente de prueba"}) {
            api.perform(get("/api/presupuestos").param("q", q).param("estado", "ACEPTADO").header("Authorization", admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].id").value(id));
        }
        api.perform(get("/api/presupuestos?pagina=1&tamano=1").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPaginas").value(2)).andExpect(jsonPath("$.contenido.length()").value(1));
        for (String filtro : new String[]{"pagina=-1", "tamano=101", "estado=OTRO", "desde=2026-12-02&hasta=2026-12-01"}) {
            api.perform(get("/api/presupuestos?" + filtro).header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
        }
    }

    @Test void limitaElPresupuestoYSusRepuestosAlTallerDeLaSesion() throws Exception {
        // Conocer IDs ajenos no permite consultar ni editar un presupuesto.
        long id = crear("/api/presupuestos", datos(items()));
        var otro = ApiFixture.registrar(api, "otro@presupuestos.test", condicion);
        api.perform(get("/api/presupuestos").header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        api.perform(get("/api/presupuestos/{id}", id).header("Authorization", otro.bearer())).andExpect(status().isNotFound());
        api.perform(put("/api/presupuestos/{id}", id).header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(items()))).andExpect(status().isNotFound());
        api.perform(patch("/api/presupuestos/{id}/estado", id).header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ANULADO\"}")).andExpect(status().isNotFound());
        String respuesta = api.perform(post("/api/repuestos").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(REPUESTO)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long repuestoAjeno = json.readTree(respuesta).get("id").asLong();
        api.perform(post("/api/presupuestos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[{\"repuestoId\":" + repuestoAjeno + ",\"cantidad\":1}]"))).andExpect(status().isNotFound());
    }

    @Test void recepcionPuedeGestionarPresupuestosYQuedaRegistrada() throws Exception {
        // Recepción registra la aceptación del cliente y queda identificada como autora.
        long id = crear("/api/presupuestos", datos(items()));
        crear("/api/usuarios", "{\"email\":\"recepcion@presupuestos.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"RECEPCION\"}");
        String respuesta = api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"recepcion@presupuestos.test\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(respuesta).get("tokenAcceso").asString();
        long usuario = jdbc.queryForObject("SELECT id FROM octano.usuario WHERE email = 'recepcion@presupuestos.test'", Long.class);
        api.perform(get("/api/presupuestos")).andExpect(status().isUnauthorized());
        api.perform(put("/api/presupuestos/{id}", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[]"))).andExpect(status().isOk()).andExpect(jsonPath("$.usuarioModificacionId").value(usuario));
        api.perform(patch("/api/presupuestos/{id}/estado", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ACEPTADO\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.estadoUsuarioId").value(usuario));
    }

    @Test void vinculaElTurnoYAnularElPresupuestoLoCancelaSinBorrarDatos() throws Exception {
        // Un presupuesto tiene como máximo un turno activo y no se edita mientras está agendado.
        long id = crear("/api/presupuestos", datos(items()));
        long turno = crear("/api/turnos", turno(id, vehiculo, manana()));
        api.perform(get("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.turnoId").value(turno));
        api.perform(put("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[]"))).andExpect(status().isBadRequest());
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(turno(id, vehiculo, manana().plusDays(1)))).andExpect(status().isBadRequest());
        estado(id, "ACEPTADO");
        estado(id, "ANULADO");
        api.perform(get("/api/turnos/{id}", turno).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.presupuestoId").value(id));
        api.perform(get("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.turnoId").isEmpty()).andExpect(jsonPath("$.items.length()").value(2));
        sinConsumo();
    }

    @Test void rechazarCancelaElTurnoYNoPermiteAgendarUnPresupuestoInvalido() throws Exception {
        // No vinculamos otro vehículo ni reutilizamos un presupuesto rechazado.
        long id = crear("/api/presupuestos", datos(items()));
        long otroVehiculo = crearVehiculo("xyz987");
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(turno(id, otroVehiculo, manana()))).andExpect(status().isBadRequest());
        var otro = ApiFixture.registrar(api, "otro@presupuestos.test", condicion);
        api.perform(post("/api/turnos").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(turno(id, vehiculo, manana()))).andExpect(status().isNotFound());
        long turno = crear("/api/turnos", turno(id, vehiculo, manana()));
        estado(id, "RECHAZADO");
        assertEquals("CANCELADO", jdbc.queryForObject("SELECT estado FROM octano.turno WHERE id = ?", String.class, turno));
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(turno(id, vehiculo, manana().plusDays(1)))).andExpect(status().isBadRequest());
    }

    @Test void noPermiteAnularElPresupuestoCuandoElVehiculoYaIngreso() throws Exception {
        // El ingreso conserva el vínculo y bloquea cambios que cancelarían un trabajo ya iniciado.
        long id = crear("/api/presupuestos", datos(items()));
        long turno = crear("/api/turnos", turno(id, vehiculo, manana()));
        api.perform(patch("/api/turnos/{id}/estado", turno).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_TALLER\"}")).andExpect(status().isOk());
        api.perform(get("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.turnoId").value(turno));
        api.perform(put("/api/presupuestos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos("[]"))).andExpect(status().isBadRequest());
        for (String destino : new String[]{"RECHAZADO", "ANULADO"}) {
            api.perform(patch("/api/presupuestos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"estado\":\"" + destino + "\"}")).andExpect(status().isBadRequest());
        }
        assertEquals("EN_TALLER", jdbc.queryForObject("SELECT estado FROM octano.turno WHERE id = ?", String.class, turno));
        assertEquals("PENDIENTE", jdbc.queryForObject("SELECT estado FROM octano.presupuesto WHERE id = ?", String.class, id));
        sinConsumo();
    }

    @Test void devuelveClienteYVehiculoEnElListadoYEnElDetalle() throws Exception {
        // El frontend recibe los nombres y la patente sin tener que resolver cada ID por separado.
        long id=crear("/api/presupuestos",datos("[]"));
        api.perform(get("/api/presupuestos/{id}",id).header("Authorization",admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteNombre").value("Cliente de prueba"))
                .andExpect(jsonPath("$.marca").value("Marca de prueba")).andExpect(jsonPath("$.modelo").value("Modelo de prueba"))
                .andExpect(jsonPath("$.patente").value("ABC-123")).andExpect(jsonPath("$.ordenId").isEmpty());
        long nuevo=crear("/api/clientes","{\"nombre\":\"Nuevo titular\",\"documento\":\"23456789\",\"telefono\":\"02392 456789\",\"condicionIvaId\":"+condicion+"}");
        api.perform(patch("/api/vehiculos/{id}/titular",vehiculo).header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":"+nuevo+"}")).andExpect(status().isOk());
        // Transferir el auto no reemplaza al cliente que solicitó este presupuesto.
        api.perform(get("/api/presupuestos").header("Authorization",admin.bearer()))
                .andExpect(jsonPath("$.contenido[0].clienteNombre").value("Cliente de prueba"))
                .andExpect(jsonPath("$.contenido[0].marca").value("Marca de prueba"))
                .andExpect(jsonPath("$.contenido[0].modelo").value("Modelo de prueba"))
                .andExpect(jsonPath("$.contenido[0].patente").value("ABC-123"));
    }

    @Test void devuelveLaOrdenVigenteSinDuplicarPresupuestosPorOrdenesCanceladas() throws Exception {
        // Al cancelar una reserva, el enlace queda vacío; al volver a reservar apunta a la nueva orden.
        long id=crear("/api/presupuestos",datos("[]"));
        long turno=crear("/api/turnos",turno(id,vehiculo,manana()));
        long primera=jdbc.queryForObject("SELECT id FROM octano.orden_trabajo WHERE turno_id=?",Long.class,turno);
        api.perform(get("/api/presupuestos/{id}",id).header("Authorization",admin.bearer()))
                .andExpect(jsonPath("$.ordenId").value(primera));
        api.perform(patch("/api/turnos/{id}/estado",turno).header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"CANCELADO\"}")).andExpect(status().isOk());
        api.perform(get("/api/presupuestos/{id}",id).header("Authorization",admin.bearer())).andExpect(jsonPath("$.ordenId").isEmpty());
        long segundo=crear("/api/turnos",turno(id,vehiculo,manana().plusDays(1)));
        long nueva=jdbc.queryForObject("SELECT id FROM octano.orden_trabajo WHERE turno_id=?",Long.class,segundo);
        api.perform(get("/api/presupuestos").param("tamano","1").header("Authorization",admin.bearer()))
                .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.contenido[0].ordenId").value(nueva));
        api.perform(get("/api/presupuestos/{id}",id).header("Authorization",admin.bearer())).andExpect(jsonPath("$.ordenId").value(nueva));
    }

    @Test void conservaElEnlaceALaOrdenDirectaDespuesDeFinalizarla() throws Exception {
        // También podemos redirigir a una orden hecha sin turno y a su trabajo ya terminado.
        long presupuesto=crear("/api/presupuestos",datos("[]"));
        long mecanico=crear("/api/mecanicos","{\"nombre\":\"Juan\",\"apellido\":\"Perez\",\"telefono\":\"02392 456789\",\"fechaInicio\":\"2020-01-01\"}");
        String respuesta=api.perform(post("/api/ordenes-trabajo").header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vehiculoId":%d,"presupuestoId":%d,"mecanicoId":%d,"descripcion":"Service",
                        "tiposTrabajo":["SERVICE"],"manoObra":50,"porcentajeIva":21}
                        """.formatted(vehiculo,presupuesto,mecanico))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long orden=json.readTree(respuesta).get("orden").get("id").asLong();
        api.perform(post("/api/ordenes-trabajo/{id}/inicio",orden).header("Authorization",admin.bearer())).andExpect(status().isOk());
        api.perform(post("/api/ordenes-trabajo/{id}/finalizacion",orden).header("Authorization",admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kilometraje\":12000,\"aceite\":\"5W-30\",\"filtros\":[]}")).andExpect(status().isOk());
        api.perform(get("/api/presupuestos/{id}",presupuesto).header("Authorization",admin.bearer()))
                .andExpect(jsonPath("$.ordenId").value(orden)).andExpect(jsonPath("$.turnoId").isEmpty());
    }

    @Test void excluyeAnuladosPorDefectoYPermiteConsultarlosExplicitamente() throws Exception {
        // El listado habitual conserva pendientes, aceptados y rechazados; los anulados tienen su filtro.
        long pendiente=crear("/api/presupuestos",datos("[]"));
        long aceptado=crear("/api/presupuestos",datos("[]")); estado(aceptado,"ACEPTADO");
        long rechazado=crear("/api/presupuestos",datos("[]")); estado(rechazado,"RECHAZADO");
        long anulado=crear("/api/presupuestos",datos("[]")); estado(anulado,"ANULADO");
        api.perform(get("/api/presupuestos").param("pagina","2").param("tamano","1").header("Authorization",admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(3)).andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.contenido[0].id").value(pendiente));
        api.perform(get("/api/presupuestos").param("estado","ANULADO").header("Authorization",admin.bearer()))
                .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].id").value(anulado));
        api.perform(get("/api/presupuestos/{id}",anulado).header("Authorization",admin.bearer())).andExpect(status().isOk());
    }

    @Test void buscaComoElFrontendSinAcentosSeparadoresNiOrdenFijoDePalabras() throws Exception {
        // Podemos escribir jose, citroen, distribucion o una patente sin guiones y obtener el mismo presupuesto.
        jdbc.update("UPDATE octano.cliente SET nombre=? WHERE id=?","José Pérez",cliente);
        long marca=jdbc.queryForObject("SELECT marca_id FROM octano.modelo WHERE id=?",Long.class,modelo);
        jdbc.update("UPDATE octano.marca SET nombre=? WHERE id=?","Citroën",marca);
        jdbc.update("UPDATE octano.modelo SET nombre=? WHERE id=?","C4 Cactus",modelo);
        long id=crear("/api/presupuestos",datos("[]").replace("Servicio de prueba","Revisión de distribución"));
        for(String q:new String[]{"JOSE","jose\u0301","PÉREZ","citroen","C4Cactus","distribucion","ABC123","perez revision","cactus jose"}) {
            api.perform(get("/api/presupuestos").param("q",q).param("tamano","1").header("Authorization",admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].id").value(id));
        }
        for(String q:new String[]{"%","' OR 1=1 --","otro cliente"}) {
            api.perform(get("/api/presupuestos").param("q",q).header("Authorization",admin.bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        }
    }
}
