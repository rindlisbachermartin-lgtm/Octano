package com.octano.backend.repuestos;

import com.octano.backend.soporte.ApiFixture;
import java.math.BigDecimal;
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
class StockConcurrenciaTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

    @Test void dosSalidasSimultaneasNoPuedenConsumirLaMismaUnidad() throws Exception {
        // Dos peticiones reales compiten por una sola unidad: solo una debe completarse.
        long condicion = jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva", Long.class);
        var cuenta = ApiFixture.registrar(api, "concurrencia-" + UUID.randomUUID() + "@prueba.test", condicion);
        try {
            String respuesta = api.perform(post("/api/repuestos").header("Authorization", cuenta.bearer())
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"codigoOem":"CONCURRENCIA","descripcion":"Repuesto de prueba","marca":"Prueba",
                             "precioCosto":10,"margen":0,"stockMinimo":0}
                            """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long id = new JsonMapper().readTree(respuesta).get("id").asLong();
            String ruta = "/api/repuestos/" + id + "/movimientos";
            api.perform(post(ruta).header("Authorization", cuenta.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tipo\":\"ENTRADA\",\"cantidad\":1,\"motivo\":\"Saldo inicial de prueba\"}"))
                    .andExpect(status().isCreated());
            var inicio = new CountDownLatch(1);
            var ejecutor = Executors.newFixedThreadPool(2);
            try {
                var salida = (java.util.concurrent.Callable<Integer>) () -> {
                    if (!inicio.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No comenzó la prueba concurrente.");
                    return api.perform(post(ruta).header("Authorization", cuenta.bearer()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tipo\":\"SALIDA\",\"cantidad\":1,\"motivo\":\"Salida simultánea de prueba\"}"))
                            .andReturn().getResponse().getStatus();
                };
                var primera = ejecutor.submit(salida);
                var segunda = ejecutor.submit(salida);
                inicio.countDown();
                var estados = List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS)).stream().sorted().toList();
                assertEquals(List.of(201, 400), estados);
            } finally {
                ejecutor.shutdownNow();
            }
            assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT stock FROM octano.repuesto WHERE id = ?", BigDecimal.class, id)));
            assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM octano.movimiento_stock WHERE repuesto_id = ?", Long.class, id).longValue());
        } finally {
            // Esta prueba confirma datos para compartirlos entre hilos y luego elimina solo su taller de prueba.
            new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
                jdbc.update("DELETE FROM octano.movimiento_stock WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.repuesto WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.usuario WHERE taller_id = ?", cuenta.tallerId());
                jdbc.update("DELETE FROM octano.taller WHERE id = ?", cuenta.tallerId());
            });
        }
    }
}
