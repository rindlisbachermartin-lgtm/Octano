package com.octano.backend.ordenes;

import com.octano.backend.soporte.ApiFixture;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrdenTrabajoApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    private final List<Long> talleresCreados=new ArrayList<>();
    private final List<Long> marcasCreadas=new ArrayList<>();
    private final JsonMapper json=new JsonMapper();
    private ApiFixture.Cuenta admin;
    private long condicion,cliente,vehiculo,modelo,mecanico,repuesto;
    private String token;

    @BeforeEach void preparar() throws Exception {
        condicion=jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva",Long.class);
        admin=ApiFixture.registrar(api,"admin@ordenes.test",condicion);
        talleresCreados.add(admin.tallerId());
        token=admin.bearer();
        cliente=crear("/api/clientes","{\"nombre\":\"Cliente Prueba\",\"documento\":\"12345678\",\"telefono\":\"02392 456789\",\"condicionIvaId\":"+condicion+"}");
        long marca=ApiFixture.marca(jdbc,"Marca de prueba");
        marcasCreadas.add(marca);
        modelo=ApiFixture.modelo(jdbc,marca,"Modelo de prueba");
        vehiculo=crear("/api/vehiculos","{\"clienteId\":"+cliente+",\"modeloId\":"+modelo+",\"patente\":\"abc123\",\"anio\":2020}");
        mecanico=crear("/api/mecanicos","{\"nombre\":\"Juan\",\"apellido\":\"Perez\",\"telefono\":\"02392 456789\",\"fechaInicio\":\"2020-01-01\"}");
        repuesto=crear("/api/repuestos","{\"codigoOem\":\"OT-001\",\"descripcion\":\"Filtro de prueba\",\"marca\":\"Prueba\",\"precioCosto\":100,\"margen\":25,\"stockMinimo\":1}");
        enviar("/api/repuestos/"+repuesto+"/movimientos","{\"tipo\":\"ENTRADA\",\"cantidad\":5,\"motivo\":\"Stock de prueba\"}").andExpect(status().isCreated());
    }
    @AfterEach void limpiar() {
        // Cada petición confirma su propia transacción, como en producción. Borramos solo los datos de esta prueba.
        new TransactionTemplate(transacciones).executeWithoutResult(estado->{
            for(long taller:talleresCreados) {
                for(String tabla:List.of("foto_orden","tarea_orden","repuesto_orden","orden_trabajo","turno","detalle_presupuesto","presupuesto",
                        "compatibilidad_repuesto","movimiento_stock","repuesto","historial_titularidad","vehiculo","cliente","usuario","mecanico")) {
                    jdbc.update("DELETE FROM octano."+tabla+" WHERE taller_id=?",taller);
                }
                jdbc.update("DELETE FROM octano.taller WHERE id=?",taller);
            }
            for(long marca:marcasCreadas) {
                jdbc.update("DELETE FROM octano.modelo WHERE marca_id=?",marca);
                jdbc.update("DELETE FROM octano.marca WHERE id=?",marca);
            }
        });
    }
    private ResultActions enviar(String ruta,String body) throws Exception {
        return api.perform(post(ruta).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
    private long crear(String ruta,String body) throws Exception {
        return leer(enviar(ruta,body).andExpect(status().isCreated())).get("id").asLong();
    }
    private String datos(String tipo,Long presupuesto) {
        return """
                {"vehiculoId":%d,"mecanicoId":%d,"presupuestoId":%s,"descripcion":"Trabajo de prueba",
                "tiposTrabajo":["%s"],"indicaciones":"Revisar pérdidas","manoObra":50,"porcentajeIva":21}
                """.formatted(vehiculo,mecanico,presupuesto,tipo);
    }
    private long orden(String tipo) throws Exception {
        return leer(enviar("/api/ordenes-trabajo",datos(tipo,null)).andExpect(status().isCreated())).get("orden").get("id").asLong();
    }
    private String ruta(long id) { return "/api/ordenes-trabajo/"+id; }
    private ResultActions consultar(long id) throws Exception { return api.perform(get(ruta(id)).header("Authorization",token)); }
    private ResultActions agregar(long id,String items) throws Exception { return enviar(ruta(id)+"/repuestos","{\"items\":"+items+"}"); }
    private String item(long id,String cantidad) { return "{\"repuestoId\":"+id+",\"cantidad\":"+cantidad+"}"; }
    private void saldo(String esperado) {
        assertEquals(0,new BigDecimal(esperado).compareTo(jdbc.queryForObject("SELECT stock FROM octano.repuesto WHERE id=?",BigDecimal.class,repuesto)));
    }
    private long turno(Long presupuesto) throws Exception {
        return crear("/api/turnos","""
                {"clienteId":%d,"vehiculoId":%d,"presupuestoId":%s,"fecha":"%s","horaInicio":"09:00","horaFin":"10:00","motivoVisita":"Revisión"}
                """.formatted(cliente,vehiculo,presupuesto,LocalDate.now().plusDays(1)));
    }
    private long ordenDelTurno(long id) {
        return jdbc.queryForObject("SELECT id FROM octano.orden_trabajo WHERE turno_id=?",Long.class,id);
    }
    private long presupuesto() throws Exception {
        return crear("/api/presupuestos","""
                {"clienteId":%d,"vehiculoId":%d,"descripcion":"Service presupuestado","manoObra":25,"porcentajeIva":21,
                "tiposTrabajo":["SERVICE"],"items":[{"repuestoId":%d,"cantidad":2,"precioUnitario":90}]}
                """.formatted(cliente,vehiculo,repuesto));
    }

    @Test void creaOrdenConDatosParaMostrarEnElFrontend() throws Exception {
        // La respuesta trae nombres, patente, tareas y quién guardó la orden.
        long id=orden("OTRO");
        consultar(id).andExpect(status().isOk()).andExpect(jsonPath("$.orden.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.orden.clienteNombre").value("Cliente Prueba"))
                .andExpect(jsonPath("$.orden.patente").value("ABC-123"))
                .andExpect(jsonPath("$.orden.marca").value("Marca de prueba"))
                .andExpect(jsonPath("$.orden.modelo").value("Modelo de prueba"))
                .andExpect(jsonPath("$.orden.mecanicoNombre").value("Juan Perez"))
                .andExpect(jsonPath("$.orden.usuarioModificacionId").value(admin.usuarioId()))
                .andExpect(jsonPath("$.tareas.length()").value(1)).andExpect(jsonPath("$.total").value(60.5));
        api.perform(get("/api/ordenes-trabajo").param("q","ABC-123").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        api.perform(get("/api/ordenes-trabajo").param("q","Cliente Prueba").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(id));
        saldo("5");
    }

    @Test void descuentaAlIniciarUnaSolaVezYRegistraElUsuario() throws Exception {
        // Planificar no gasta stock. Iniciar dos veces tampoco descuenta dos veces.
        long id=orden("OTRO");
        agregar(id,"["+item(repuesto,"2")+"]").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(363));
        saldo("5");
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].consumido").value(true));
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        saldo("3");
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM octano.movimiento_stock WHERE repuesto_id=? AND tipo='SALIDA' AND usuario_id=?",Long.class,repuesto,admin.usuarioId()));
    }

    @Test void revierteTodoSiNoAlcanzaElStockAlIniciar() throws Exception {
        // Si falla un material, se deshacen también las salidas que ya se habían calculado.
        long otro=crear("/api/repuestos","{\"codigoOem\":\"SIN-STOCK\",\"descripcion\":\"Sin stock\",\"marca\":\"Prueba\",\"precioCosto\":1,\"margen\":0,\"stockMinimo\":0}");
        long id=orden("OTRO");
        agregar(id,"["+item(repuesto,"2")+","+item(otro,"1")+"]").andExpect(status().isOk());
        enviar(ruta(id)+"/inicio","").andExpect(status().isBadRequest());
        consultar(id).andExpect(jsonPath("$.orden.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.items[0].consumido").value(false));
        saldo("5");
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM octano.movimiento_stock WHERE repuesto_id=? AND tipo='SALIDA'",Long.class,repuesto));
    }

    @Test void losAgregadosDuranteElTrabajoDescuentanYFallanSinDejarRenglones() throws Exception {
        // Un agregado imposible no deja materiales guardados ni cambia el saldo.
        long id=orden("OTRO");
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        agregar(id,"["+item(repuesto,"2")+"]").andExpect(status().isOk());
        agregar(id,"["+item(repuesto,"4")+"]").andExpect(status().isBadRequest());
        saldo("3");
        consultar(id).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test void cierraElServiceConAceiteYActualizaElKilometraje() throws Exception {
        // Para un service se exige aceite. El cierre guarda también los filtros y kilómetros.
        long id=orden("SERVICE");
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        enviar(ruta(id)+"/finalizacion","{\"kilometraje\":25000,\"filtros\":[]}").andExpect(status().isBadRequest());
        enviar(ruta(id)+"/finalizacion","{\"kilometraje\":25000,\"aceite\":\"5W-30\",\"filtros\":[\"ACEITE\"],\"observacionesMecanico\":\"Sin pérdidas\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.orden.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.orden.aceite").value("5W-30")).andExpect(jsonPath("$.orden.finFecha").isNotEmpty());
        assertEquals(25000,jdbc.queryForObject("SELECT kilometraje FROM octano.vehiculo WHERE id=?",Integer.class,vehiculo));
        assertEquals(admin.usuarioId(),jdbc.queryForObject("SELECT usuario_modificacion_id FROM octano.vehiculo WHERE id=?",Long.class,vehiculo));
        enviar(ruta(id)+"/finalizacion","{\"kilometraje\":1,\"aceite\":\"Otro\",\"filtros\":[]}").andExpect(status().isBadRequest());
        agregar(id,"["+item(repuesto,"1")+"]").andExpect(status().isBadRequest());
        enviar(ruta(id)+"/cancelacion","{\"motivo\":\"Prueba\"}").andExpect(status().isBadRequest());
    }

    @Test void registraDiagnosticoYTareasSoloDuranteElTrabajo() throws Exception {
        // Las tareas completadas determinan el porcentaje de avance.
        long id=orden("OTRO");
        long tarea=leer(consultar(id)).get("tareas").get(0).get("id").asLong();
        api.perform(patch(ruta(id)+"/tareas/"+tarea).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"completada\":true}"))
                .andExpect(status().isBadRequest());
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        api.perform(put(ruta(id)+"/trabajo").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnostico\":\"Correa gastada\",\"kilometraje\":12000,\"observacionesMecanico\":\"Revisado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orden.diagnostico").value("Correa gastada"));
        api.perform(patch(ruta(id)+"/tareas/"+tarea).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"completada\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.progreso").value(100));
        enviar(ruta(id)+"/finalizacion","{\"kilometraje\":12000,\"filtros\":[]}").andExpect(status().isOk());
    }

    @Test void copiaLosPreciosDelPresupuestoSinConsumirAntesDeIniciar() throws Exception {
        // La orden conserva el precio presupuestado aunque cambie el catálogo.
        long presupuesto=presupuesto();
        long id=leer(enviar("/api/ordenes-trabajo",datos("SERVICE",presupuesto)).andExpect(status().isCreated())).get("orden").get("id").asLong();
        consultar(id).andExpect(jsonPath("$.items[0].precioUnitario").value(90))
                .andExpect(jsonPath("$.items[0].presupuestado").value(true)).andExpect(jsonPath("$.orden.manoObra").value(25))
                .andExpect(jsonPath("$.total").value(248.05));
        saldo("5");
        enviar("/api/ordenes-trabajo",datos("SERVICE",presupuesto)).andExpect(status().isBadRequest());
        api.perform(patch("/api/presupuestos/"+presupuesto+"/estado").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ANULADO\"}")).andExpect(status().isBadRequest());
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        saldo("3");
    }

    @Test void elTurnoCreaUnaOrdenYElIngresoSeSincroniza() throws Exception {
        // El turno emite una orden pendiente; ingresar por cualquiera de las dos APIs actualiza ambas.
        long turno=turno(null),id=ordenDelTurno(turno);
        consultar(id).andExpect(jsonPath("$.orden.estado").value("PENDIENTE_INGRESO"))
                .andExpect(jsonPath("$.orden.fechaTurno").isNotEmpty()).andExpect(jsonPath("$.orden.horaInicioTurno").value("09:00:00"));
        api.perform(get("/api/turnos/"+turno).header("Authorization",token)).andExpect(jsonPath("$.ordenId").value(id));
        api.perform(put(ruta(id)).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(datos("OTRO",null)))
                .andExpect(status().isOk());
        enviar(ruta(id)+"/ingreso","").andExpect(status().isOk()).andExpect(jsonPath("$.orden.estado").value("EN_ESPERA"));
        assertEquals("EN_TALLER",jdbc.queryForObject("SELECT estado FROM octano.turno WHERE id=?",String.class,turno));
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        enviar(ruta(id)+"/finalizacion","{\"kilometraje\":1000,\"filtros\":[]}").andExpect(status().isOk());
        assertEquals("FINALIZADO",jdbc.queryForObject("SELECT estado FROM octano.turno WHERE id=?",String.class,turno));
    }

    @Test void cancelarElTurnoOElPresupuestoCancelaLaOrdenPendiente() throws Exception {
        // La baja no debe dejar órdenes pendientes para reservas que ya no existen.
        long presupuesto=presupuesto(),turno=turno(presupuesto),id=ordenDelTurno(turno);
        api.perform(patch("/api/presupuestos/"+presupuesto+"/estado").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"ANULADO\"}")).andExpect(status().isOk());
        consultar(id).andExpect(jsonPath("$.orden.estado").value("CANCELADO"));
        saldo("5");
        turno=turno(null);id=ordenDelTurno(turno);
        api.perform(patch("/api/turnos/"+turno+"/estado").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"CANCELADO\"}")).andExpect(status().isOk());
        consultar(id).andExpect(jsonPath("$.orden.estado").value("CANCELADO"));
    }

    @Test void devuelveUnaSolaVezUnRepuestoInclusoSiFueArchivado() throws Exception {
        // Quitar un material usado genera una entrada; repetir la petición no repone otra vez.
        long id=orden("OTRO");
        agregar(id,"["+item(repuesto,"2")+"]").andExpect(status().isOk());
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        long item=leer(consultar(id)).get("items").get(0).get("id").asLong();
        jdbc.update("UPDATE octano.repuesto SET activo=FALSE WHERE id=?",repuesto);
        api.perform(delete(ruta(id)+"/repuestos/"+item).header("Authorization",token)).andExpect(status().isOk());
        api.perform(delete(ruta(id)+"/repuestos/"+item).header("Authorization",token)).andExpect(status().isNotFound());
        saldo("5");
    }

    @Test void cancelarUnaOrdenIniciadaNoDevuelveMaterialesUtilizados() throws Exception {
        // Cancelar el trabajo no significa que los materiales usados estén disponibles otra vez.
        long id=orden("OTRO");
        agregar(id,"["+item(repuesto,"2")+"]").andExpect(status().isOk());
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        enviar(ruta(id)+"/cancelacion","{\"motivo\":\"Cliente decidió no continuar\"}").andExpect(status().isOk());
        enviar(ruta(id)+"/cancelacion","{\"motivo\":\"Repetido\"}").andExpect(status().isOk());
        saldo("3");
        enviar(ruta(id)+"/inicio","").andExpect(status().isBadRequest());
    }

    @Test void elMecanicoSoloOperaSusOrdenesYNoCambiaPrecios() throws Exception {
        // La sesión del mecánico limita la lista y cada operación a sus propias órdenes.
        long id=orden("OTRO");
        crear("/api/usuarios","{\"email\":\"mecanico@ordenes.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"MECANICO\",\"mecanicoId\":"+mecanico+"}");
        String login=api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"mecanico@ordenes.test\",\"password\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token="Bearer "+json.readTree(login).get("tokenAcceso").asString();
        consultar(id).andExpect(status().isOk());
        api.perform(get("/api/ordenes-trabajo").param("mecanicoId","999999").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        enviar("/api/ordenes-trabajo",datos("OTRO",null)).andExpect(status().isForbidden());
        enviar(ruta(id)+"/cancelacion","{\"motivo\":\"Prueba\"}").andExpect(status().isForbidden());
        agregar(id,"[{\"repuestoId\":"+repuesto+",\"cantidad\":1,\"precioUnitario\":0}]").andExpect(status().isForbidden());
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        agregar(id,"["+item(repuesto,"1")+"]").andExpect(status().isOk());
        saldo("4");
        var otro=ApiFixture.registrar(api,"otro@ordenes.test",condicion);
        talleresCreados.add(otro.tallerId());
        token=otro.bearer();
        consultar(id).andExpect(status().isNotFound());
        enviar(ruta(id)+"/inicio","").andExpect(status().isNotFound());
        api.perform(get("/api/ordenes-trabajo").header("Authorization",token)).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test void validaCompatibilidadDatosYEstados() throws Exception {
        // Se rechazan repuestos incompatibles y números inválidos antes de guardar.
        long id=orden("OTRO"),otraMarca=ApiFixture.marca(jdbc,"Otra marca"),otroModelo=ApiFixture.modelo(jdbc,otraMarca,"Otro modelo");
        marcasCreadas.add(otraMarca);
        enviar("/api/repuestos/"+repuesto+"/compatibilidades","{\"modeloId\":"+otroModelo+"}").andExpect(status().isCreated());
        agregar(id,"["+item(repuesto,"1")+"]").andExpect(status().isBadRequest());
        agregar(id,"["+item(repuesto,"0")+"]").andExpect(status().isBadRequest());
        enviar(ruta(id)+"/finalizacion","{\"kilometraje\":0,\"filtros\":[]}").andExpect(status().isBadRequest());
        enviar("/api/ordenes-trabajo",datos("OTRO",null).replace("\"mecanicoId\":"+mecanico,"\"mecanicoId\":999999"))
                .andExpect(status().isNotFound());
        api.perform(get("/api/ordenes-trabajo").param("tamano","101").header("Authorization",token)).andExpect(status().isBadRequest());
        api.perform(get("/api/ordenes-trabajo")).andExpect(status().isUnauthorized());
        enviar(ruta(id)+"/inicio","").andExpect(status().isOk());
        enviar(ruta(id)+"/finalizacion","{\"kilometraje\":1.5,\"filtros\":[]}").andExpect(status().isBadRequest());
    }

    @Test void dosOrdenesNoConsumenLasMismasUnidadesAlMismoTiempo() throws Exception {
        // Hay cinco unidades: dos órdenes que piden cuatro no pueden iniciar las dos.
        long primera=orden("OTRO"),segunda=orden("OTRO");
        agregar(primera,"["+item(repuesto,"4")+"]").andExpect(status().isOk());
        agregar(segunda,"["+item(repuesto,"4")+"]").andExpect(status().isOk());
        var inicio=new CountDownLatch(1);
        try(var ejecutor=Executors.newFixedThreadPool(2)) {
            var a=ejecutor.submit(()->{ inicio.await(5,TimeUnit.SECONDS);return enviar(ruta(primera)+"/inicio","").andReturn().getResponse().getStatus(); });
            var b=ejecutor.submit(()->{ inicio.await(5,TimeUnit.SECONDS);return enviar(ruta(segunda)+"/inicio","").andReturn().getResponse().getStatus(); });
            inicio.countDown();
            assertEquals(List.of(200,400),List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)).stream().sorted().toList());
        }
        saldo("1");
    }

    @Test void guardaFotosValidasYProtegeSuContenido() throws Exception {
        // La foto se valida como imagen real y solo se entrega a quien puede consultar la orden.
        long id=orden("OTRO");
        var salida=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",salida);
        String datos="data:image/png;base64,"+Base64.getEncoder().encodeToString(salida.toByteArray());
        var respuesta=leer(enviar(ruta(id)+"/fotos","{\"sector\":\"Frente\",\"datos\":\""+datos+"\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos[0].usuarioId").value(admin.usuarioId())));
        long foto=respuesta.get("fotos").get(0).get("id").asLong();
        api.perform(get(ruta(id)+"/fotos/"+foto).header("Authorization",token)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(salida.toByteArray()))
                .andExpect(header().string("Cache-Control","no-store"));
        enviar(ruta(id)+"/fotos","{\"sector\":\"Frente\",\"datos\":\"data:image/png;base64,aG9sYQ==\"}").andExpect(status().isBadRequest());
        enviar(ruta(id)+"/fotos","{\"sector\":\"Frente\",\"datos\":\"data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(salida.toByteArray())+"\"}")
                .andExpect(status().isBadRequest());
        var otro=ApiFixture.registrar(api,"fotos-ajenas@ordenes.test",condicion);
        talleresCreados.add(otro.tallerId());
        api.perform(get(ruta(id)+"/fotos/"+foto).header("Authorization",otro.bearer())).andExpect(status().isNotFound());
        api.perform(delete(ruta(id)+"/fotos/"+foto).header("Authorization",token)).andExpect(status().isOk());
        api.perform(get(ruta(id)+"/fotos/"+foto).header("Authorization",token)).andExpect(status().isNotFound());
    }

    @Test void unMecanicoNoPuedeOperarLaOrdenDeOtroMecanicoDelMismoTaller() throws Exception {
        // Pertenecer al mismo taller no alcanza: el mecánico debe estar asignado a esa orden.
        long id=orden("OTRO");
        long otroMecanico=crear("/api/mecanicos","{\"nombre\":\"Pedro\",\"apellido\":\"Lopez\",\"telefono\":\"02392 456789\",\"fechaInicio\":\"2020-01-01\"}");
        crear("/api/usuarios","{\"email\":\"otro-mecanico@ordenes.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"MECANICO\",\"mecanicoId\":"+otroMecanico+"}");
        var login=leer(api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"otro-mecanico@ordenes.test\",\"password\":\"ClaveDePrueba123!\"}")).andExpect(status().isOk()));
        token="Bearer "+login.get("tokenAcceso").asString();
        consultar(id).andExpect(status().isNotFound());
        enviar(ruta(id)+"/inicio","").andExpect(status().isNotFound());
        agregar(id,"["+item(repuesto,"1")+"]").andExpect(status().isNotFound());
        api.perform(get("/api/ordenes-trabajo").param("mecanicoId",String.valueOf(mecanico)).header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
    }
}
