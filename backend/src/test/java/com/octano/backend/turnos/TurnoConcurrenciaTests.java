package com.octano.backend.turnos;

import com.octano.backend.soporte.ApiFixture;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TurnoConcurrenciaTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

    @Test void dosReservasSimultaneasRequierenAvisarLaSuperposicion() throws Exception {
        // Sin autorización, solo una reserva se guarda; la otra debe mostrar el aviso 409.
        probar(false);
    }
    @Test void dosReservasSimultaneasSePermitenConAutorizacionDelAdmin() throws Exception {
        // Con confirmación de ADMIN, se guardan ambos vehículos y queda una superposición autorizada.
        probar(true);
    }
    private long crear(String ruta, String body, String token) throws Exception {
        String respuesta = api.perform(post(ruta).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new JsonMapper().readTree(respuesta).get("id").asLong();
    }
    private void probar(boolean confirmar) throws Exception {
        String identificador = UUID.randomUUID().toString();
        long condicion = jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva", Long.class);
        var cuenta = ApiFixture.registrar(api, "agenda-" + identificador + "@prueba.test", condicion);
        Long[] catalogo = new Long[2];
        try {
            catalogo[0] = ApiFixture.marca(jdbc, "Agenda " + identificador);
            catalogo[1] = ApiFixture.modelo(jdbc, catalogo[0], "Modelo");
            long cliente = crear("/api/clientes", "{\"nombre\":\"Prueba\",\"documento\":\"12345678\",\"telefono\":\"02392 456789\",\"condicionIvaId\":" + condicion + "}", cuenta.bearer());
            long primero = crear("/api/vehiculos", "{\"clienteId\":" + cliente + ",\"modeloId\":" + catalogo[1] + ",\"patente\":\"abc123\",\"anio\":2020,\"kilometraje\":0}", cuenta.bearer());
            long segundo = crear("/api/vehiculos", "{\"clienteId\":" + cliente + ",\"modeloId\":" + catalogo[1] + ",\"patente\":\"xyz987\",\"anio\":2020,\"kilometraje\":0}", cuenta.bearer());
            LocalDate fecha = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
            var inicio = new CountDownLatch(1);
            var ejecutor = Executors.newFixedThreadPool(2);
            try {
                var primera = ejecutor.submit(() -> reservar(cuenta.bearer(), cliente, primero, fecha, confirmar, inicio));
                var segunda = ejecutor.submit(() -> reservar(cuenta.bearer(), cliente, segundo, fecha, confirmar, inicio));
                inicio.countDown();
                var estados = List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS)).stream().sorted().toList();
                assertEquals(confirmar ? List.of(201, 201) : List.of(201, 409), estados);
            } finally {
                ejecutor.shutdownNow();
            }
            assertEquals(confirmar ? 2L : 1L, jdbc.queryForObject("SELECT count(*) FROM octano.turno WHERE taller_id = ?", Long.class, cuenta.tallerId()).longValue());
            assertEquals(confirmar ? 1L : 0L, jdbc.queryForObject("SELECT count(*) FROM octano.turno WHERE taller_id = ? AND superposicion_autorizada", Long.class, cuenta.tallerId()).longValue());
        } finally {
            // Estas peticiones confirman datos para compartirlos entre hilos; limpiamos solo su taller y catálogo temporal.
            new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
                jdbc.update("DELETE FROM octano.foto_orden WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.repuesto_orden WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.tarea_orden WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.orden_trabajo WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.turno WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.historial_titularidad WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.vehiculo WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.cliente WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.usuario WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.taller WHERE id = ?", cuenta.tallerId());
                if (catalogo[1] != null) jdbc.update("DELETE FROM octano.modelo WHERE id = ?", catalogo[1]);
                if (catalogo[0] != null) jdbc.update("DELETE FROM octano.marca WHERE id = ?", catalogo[0]);
            });
        }
    }
    private int reservar(String token, long cliente, long vehiculo, LocalDate fecha, boolean confirmar, CountDownLatch inicio) throws Exception {
        if (!inicio.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No comenzó la prueba concurrente.");
        return api.perform(post("/api/turnos").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("""
                {"clienteId":%d,"vehiculoId":%d,"fecha":"%s","horaInicio":"09:00","horaFin":"10:00",
                 "motivoVisita":"Prueba concurrente","confirmarSuperposicion":%s}
                """.formatted(cliente, vehiculo, fecha, confirmar))).andReturn().getResponse().getStatus();
    }
}
