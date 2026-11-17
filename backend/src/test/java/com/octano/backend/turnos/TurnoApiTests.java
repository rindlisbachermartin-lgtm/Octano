package com.octano.backend.turnos;

import com.octano.backend.soporte.ApiFixture;
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
class TurnoApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = new JsonMapper();
    private ApiFixture.Cuenta admin;
    private long condicion, cliente, vehiculo, segundoVehiculo;
    private LocalDate fecha;

    @BeforeEach void preparar() throws Exception {
        condicion = jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva", Long.class);
        admin = ApiFixture.registrar(api, "admin@turnos.test", condicion);
        cliente = crear("/api/clientes", """
                {"nombre":"Cliente de prueba","documento":"12345678","telefono":"02392 456789","condicionIvaId":%d}
                """.formatted(condicion));
        long marca = ApiFixture.marca(jdbc, "Marca de prueba");
        long modelo = ApiFixture.modelo(jdbc, marca, "Modelo de prueba");
        vehiculo = crearVehiculo("abc123", modelo);
        segundoVehiculo = crearVehiculo("xyz987", modelo);
        fecha = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
    }
    private long crear(String ruta, String body) throws Exception {
        String respuesta = api.perform(post(ruta).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }
    private long crearVehiculo(String patente, long modelo) throws Exception {
        return crear("/api/vehiculos", """
                {"clienteId":%d,"modeloId":%d,"patente":"%s","anio":2020,"kilometraje":10000}
                """.formatted(cliente, modelo, patente));
    }
    private String datos(long vehiculoId, String inicio, String fin, boolean confirmar) {
        return """
                {"clienteId":%d,"vehiculoId":%d,"fecha":"%s","horaInicio":"%s","horaFin":"%s",
                 "motivoVisita":" Servicio de prueba ","confirmarSuperposicion":%s}
                """.formatted(cliente, vehiculoId, fecha, inicio, fin, confirmar);
    }
    private void estado(long id, String estado) throws Exception {
        api.perform(patch("/api/turnos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado));
    }
    private String recepcion() throws Exception {
        crear("/api/usuarios", "{\"email\":\"recepcion@turnos.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"RECEPCION\"}");
        String respuesta = api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"recepcion@turnos.test\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(respuesta).get("tokenAcceso").asString();
    }

    @Test void creaConsultaYConfirmaUnTurnoConAuditoria() throws Exception {
        // El turno comienza programado y registra al usuario que lo creó y confirmó.
        String respuesta = api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "09:00", "10:00", false))).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.motivoVisita").value("Servicio de prueba"))
                .andExpect(jsonPath("$.superposicionAutorizada").value(false))
                .andExpect(jsonPath("$.usuarioModificacionId").value(admin.usuarioId()))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        api.perform(get("/api/turnos/{id}", id).header("Authorization", admin.bearer())).andExpect(status().isOk());
        estado(id, "CONFIRMADO");
        assertEquals("CONFIRMADO", jdbc.queryForObject("SELECT estado FROM octano.turno WHERE id = ?", String.class, id));
    }

    @Test void reprogramaYExcluyeElPropioTurnoAlConsultarDisponibilidad() throws Exception {
        // Editar el mismo turno no debe confundirlo con otro que ocupa el horario.
        long id = crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:30")
                .param("horaFin", "10:30").param("excluirTurnoId", Long.toString(id)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true));
        api.perform(put("/api/turnos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "11:00", "12:00", false))).andExpect(status().isOk());
        assertEquals("11:00:00", jdbc.queryForObject("SELECT hora_inicio::text FROM octano.turno WHERE id = ?", String.class, id));
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:00")
                .param("horaFin", "10:00").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true));
    }

    @Test void avisaLaSuperposicionYSoloLaGuardaConConfirmacionDelAdministrador() throws Exception {
        // Primero devolvemos el aviso; al confirmar guardamos también quién lo autorizó.
        long primero = crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(segundoVehiculo, "09:30", "10:30", false))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUPERPOSICION_TURNO"))
                .andExpect(jsonPath("$.requiereConfirmacion").value(true)).andExpect(jsonPath("$.puedeConfirmar").value(true))
                .andExpect(jsonPath("$.conflictos[0].id").value(primero));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM octano.turno WHERE taller_id = ?", Long.class, admin.tallerId()).longValue());
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(segundoVehiculo, "09:30", "10:30", true))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.superposicionAutorizada").value(true))
                .andExpect(jsonPath("$.autorizadaPor").value(admin.usuarioId())).andExpect(jsonPath("$.autorizadaFecha").exists());
        // Ni un administrador puede duplicar el mismo vehículo en el mismo día.
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "15:00", "16:00", true))).andExpect(status().isBadRequest());
    }

    @Test void laReprogramacionTambienRequiereConfirmarUnaSuperposicion() throws Exception {
        // Un intento rechazado no cambia el horario que ya estaba guardado.
        crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        long id = crear("/api/turnos", datos(segundoVehiculo, "11:00", "12:00", false));
        api.perform(put("/api/turnos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(segundoVehiculo, "09:30", "10:30", false))).andExpect(status().isConflict());
        assertEquals("11:00:00", jdbc.queryForObject("SELECT hora_inicio::text FROM octano.turno WHERE id = ?", String.class, id));
        api.perform(put("/api/turnos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(segundoVehiculo, "09:30", "10:30", true))).andExpect(status().isOk())
                .andExpect(jsonPath("$.superposicionAutorizada").value(true));
    }

    @Test void recepcionPuedeAgendarPeroNoAutorizarSuperposiciones() throws Exception {
        // El permiso se verifica en el backend aunque alguien envíe el indicador manualmente.
        crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        String token = recepcion();
        api.perform(post("/api/turnos").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(datos(segundoVehiculo, "09:30", "10:30", false))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.puedeConfirmar").value(false));
        api.perform(post("/api/turnos").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(datos(segundoVehiculo, "09:30", "10:30", true))).andExpect(status().isForbidden());
        api.perform(post("/api/turnos").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(datos(segundoVehiculo, "10:00", "11:00", false))).andExpect(status().isCreated());
        api.perform(get("/api/turnos").header("Authorization", token)).andExpect(status().isOk());
        api.perform(get("/api/turnos")).andExpect(status().isUnauthorized());
    }

    @Test void cancelarLiberaElHorarioYNoPermiteReactivarElTurno() throws Exception {
        // Una cancelación conserva el registro pero deja disponible la franja horaria.
        long id = crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        estado(id, "CANCELADO");
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:00")
                .param("horaFin", "10:00").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true));
        crear("/api/turnos", datos(segundoVehiculo, "09:00", "10:00", false));
        api.perform(patch("/api/turnos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"PROGRAMADO\"}")).andExpect(status().isBadRequest());
        api.perform(put("/api/turnos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "14:00", "15:00", false))).andExpect(status().isBadRequest());
    }

    @Test void noAsistioSoloSePermiteCuandoTerminoElHorario() throws Exception {
        // No marcamos una ausencia antes de que haya terminado el turno.
        long id = crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        api.perform(patch("/api/turnos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"NO_ASISTIO\"}")).andExpect(status().isBadRequest());
        // Simulamos que ya pasó la fecha, sin hacer esperar la prueba.
        jdbc.update("UPDATE octano.turno SET fecha = ? WHERE id = ?", fecha.minusDays(2), id);
        estado(id, "NO_ASISTIO");
    }

    @Test void validaHorarioDatosActivosYTitularDelVehiculo() throws Exception {
        // Rechazamos horas invertidas, fechas pasadas y campos incompletos.
        String base = datos(vehiculo, "09:00", "10:00", false);
        for (String body : new String[]{"{}", datos(vehiculo, "10:00", "09:00", false), datos(vehiculo, "09:00", "09:00", false),
                datos(vehiculo, "09:00:01", "10:00", false), base.replace(fecha.toString(), fecha.minusDays(2).toString()),
                base.replace(" Servicio de prueba ", " ")}) {
            api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        long otroCliente = crear("/api/clientes", "{\"nombre\":\"Otro cliente\",\"documento\":\"23456789\",\"telefono\":\"02392 456789\",\"condicionIvaId\":" + condicion + "}");
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(base.replace("\"clienteId\":" + cliente, "\"clienteId\":" + otroCliente))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE octano.cliente SET activo = FALSE WHERE id = ?", cliente);
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(base)).andExpect(status().isBadRequest());
    }

    @Test void filtraLaAgendaPorFechaVehiculoYEstadoConPaginacion() throws Exception {
        // Se puede consultar un rango de fechas y navegar por páginas.
        long id = crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        crear("/api/turnos", datos(segundoVehiculo, "10:00", "11:00", false));
        estado(id, "CONFIRMADO");
        api.perform(get("/api/turnos").param("desde", fecha.toString()).param("hasta", fecha.toString()).param("estado", "CONFIRMADO")
                .param("vehiculoId", Long.toString(vehiculo)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(id));
        api.perform(get("/api/turnos?pagina=1&tamano=1").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPaginas").value(2)).andExpect(jsonPath("$.contenido.length()").value(1));
        for (String filtro : new String[]{"pagina=-1", "tamano=101", "estado=OTRO", "clienteId=0", "desde=" + fecha.plusDays(1) + "&hasta=" + fecha}) {
            api.perform(get("/api/turnos?" + filtro).header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
        }
    }

    @Test void otroTallerNoPuedeConsultarCambiarNiUsarNuestrosTurnosYVehiculos() throws Exception {
        // El aviso de disponibilidad tampoco debe filtrar turnos de otros talleres.
        long id = crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        var otro = ApiFixture.registrar(api, "otro@turnos.test", condicion);
        api.perform(get("/api/turnos").header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        api.perform(get("/api/turnos/{id}", id).header("Authorization", otro.bearer())).andExpect(status().isNotFound());
        api.perform(put("/api/turnos/{id}", id).header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "11:00", "12:00", false))).andExpect(status().isNotFound());
        api.perform(patch("/api/turnos/{id}/estado", id).header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"CANCELADO\"}")).andExpect(status().isNotFound());
        api.perform(post("/api/turnos").header("Authorization", otro.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "09:00", "10:00", true))).andExpect(status().isNotFound());
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:00")
                .param("horaFin", "10:00").header("Authorization", otro.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conflictos.length()").value(0));
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:00")
                .param("horaFin", "10:00").param("excluirTurnoId", Long.toString(id)).header("Authorization", otro.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test void devuelveLosDatosParaMostrarElTurnoYSusSuperposiciones() throws Exception {
        // La respuesta incluye los nombres y la patente, sin consultas adicionales del frontend.
        jdbc.update("UPDATE octano.vehiculo SET kilometraje = NULL WHERE id = ?", vehiculo);
        String respuesta = api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "09:00", "10:00", false))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteNombre").value("Cliente de prueba"))
                .andExpect(jsonPath("$.patente").value("ABC-123"))
                .andExpect(jsonPath("$.marca").value("Marca de prueba"))
                .andExpect(jsonPath("$.modelo").value("Modelo de prueba"))
                .andExpect(jsonPath("$.anio").value(2020)).andExpect(jsonPath("$.kilometraje").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        api.perform(get("/api/turnos/{id}", id).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteNombre").value("Cliente de prueba"));
        api.perform(get("/api/turnos").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].patente").value("ABC-123"));
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(segundoVehiculo, "09:30", "10:30", false))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictos[0].patente").value("ABC-123"))
                .andExpect(jsonPath("$.conflictos[0].clienteNombre").value("Cliente de prueba"));
    }

    @Test void disponibilidadDetectaQueElVehiculoYaTieneTurnoAunqueElHorarioSeaOtro() throws Exception {
        // No alcanza con mirar la hora: el mismo vehículo solo puede tener un turno activo por día.
        long id = crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "15:00")
                .param("horaFin", "16:00").param("vehiculoId", Long.toString(vehiculo)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.vehiculoConTurno").value(true)).andExpect(jsonPath("$.requiereConfirmacion").value(false))
                .andExpect(jsonPath("$.conflictos.length()").value(0)).andExpect(jsonPath("$.turnosDelVehiculo[0].id").value(id));
        // Al editar el propio turno, se lo excluye de las dos comprobaciones.
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:00")
                .param("horaFin", "10:00").param("vehiculoId", Long.toString(vehiculo)).param("excluirTurnoId", Long.toString(id))
                .header("Authorization", admin.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true)).andExpect(jsonPath("$.vehiculoConTurno").value(false));
        // Otro vehículo puede usar la misma franja si ADMIN confirma la superposición.
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:00")
                .param("horaFin", "10:00").param("vehiculoId", Long.toString(segundoVehiculo)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehiculoConTurno").value(false))
                .andExpect(jsonPath("$.requiereConfirmacion").value(true));
    }

    @Test void disponibilidadValidaElVehiculoDentroDelTaller() throws Exception {
        // No consultamos disponibilidad de un vehículo ajeno o inexistente.
        var otro = ApiFixture.registrar(api, "otro@turnos.test", condicion);
        for (String token : new String[]{otro.bearer(), admin.bearer()}) {
            long id = token.equals(admin.bearer()) ? 999999 : vehiculo;
            api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:00")
                    .param("horaFin", "10:00").param("vehiculoId", Long.toString(id)).header("Authorization", token))
                    .andExpect(status().isNotFound());
        }
        jdbc.update("UPDATE octano.vehiculo SET activo = FALSE WHERE id = ?", vehiculo);
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "09:00")
                .param("horaFin", "10:00").param("vehiculoId", Long.toString(vehiculo)).header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test void enTallerRegistraElIngresoYNoPermiteVolverAOperacionesDeReserva() throws Exception {
        // El ingreso es una acción explícita; después no se reprograma ni se marca como ausente.
        long id = crear("/api/turnos", datos(vehiculo, "09:00", "10:00", false));
        estado(id, "EN_TALLER");
        api.perform(get("/api/turnos?estado=EN_TALLER").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(id))
                .andExpect(jsonPath("$.contenido[0].usuarioModificacionId").value(admin.usuarioId()));
        for (String destino : new String[]{"PROGRAMADO", "CONFIRMADO", "CANCELADO", "NO_ASISTIO"}) {
            api.perform(patch("/api/turnos/{id}/estado", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"estado\":\"" + destino + "\"}")).andExpect(status().isBadRequest());
        }
        api.perform(put("/api/turnos/{id}", id).header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "11:00", "12:00", false))).andExpect(status().isBadRequest());
        api.perform(post("/api/turnos").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(datos(vehiculo, "15:00", "16:00", true))).andExpect(status().isBadRequest());
        api.perform(get("/api/turnos/disponibilidad").param("fecha", fecha.toString()).param("horaInicio", "15:00")
                .param("horaFin", "16:00").param("vehiculoId", Long.toString(vehiculo)).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehiculoConTurno").value(true));
    }
}
