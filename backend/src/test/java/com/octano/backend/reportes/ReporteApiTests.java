package com.octano.backend.reportes;

import com.octano.backend.soporte.ApiFixture;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReporteApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    private final JsonMapper json=new JsonMapper();
    private final List<Long> talleres=new ArrayList<>();
    private ApiFixture.Cuenta admin;
    private String token;
    private long condicion,marca,modelo,cliente,vehiculo,mecanico;
    @BeforeEach void preparar() throws Exception {
        condicion=jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva",Long.class);
        admin=cuenta("admin@reportes.test"); token=admin.bearer();
        cliente=crear("/api/clientes","{\"nombre\":\"Cliente reportes\",\"documento\":\"12345678\",\"telefono\":\"02392 456789\",\"condicionIvaId\":"+condicion+"}");
        marca=ApiFixture.marca(jdbc,"Marca reportes"); modelo=ApiFixture.modelo(jdbc,marca,"Modelo reportes");
        vehiculo=crear("/api/vehiculos","{\"clienteId\":"+cliente+",\"modeloId\":"+modelo+",\"patente\":\"abc123\",\"anio\":2020}");
        mecanico=crear("/api/mecanicos","{\"nombre\":\"Juan\",\"apellido\":\"Perez\",\"telefono\":\"02392 456789\",\"fechaInicio\":\"2020-01-01\"}");
    }
    private ApiFixture.Cuenta cuenta(String email) throws Exception {
        var c=ApiFixture.registrar(api,email,condicion); talleres.add(c.tallerId()); return c;
    }
    @AfterEach void limpiar() {
        new TransactionTemplate(transacciones).executeWithoutResult(estado->{
            // Borramos únicamente los talleres temporales de esta prueba.
            for(long taller:talleres) {
                for(String tabla:List.of("cobro","detalle_comprobante","comprobante","codigo_qr","foto_orden","tarea_orden","repuesto_orden",
                        "orden_trabajo","turno","detalle_presupuesto","presupuesto","compatibilidad_repuesto","movimiento_stock","repuesto",
                        "historial_titularidad","vehiculo","cliente","usuario","mecanico")) jdbc.update("DELETE FROM octano."+tabla+" WHERE taller_id=?",taller);
                jdbc.update("DELETE FROM octano.taller WHERE id=?",taller);
            }
            jdbc.update("DELETE FROM octano.modelo WHERE id=?",modelo); jdbc.update("DELETE FROM octano.marca WHERE id=?",marca);
        });
    }
    private JsonNode leer(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }
    private ResultActions enviar(String ruta,String body) throws Exception {
        return api.perform(post(ruta).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private long crear(String ruta,String body) throws Exception { return leer(enviar(ruta,body).andExpect(status().isCreated())).get("id").asLong(); }
    private ResultActions reporte(String tipo) throws Exception {
        return api.perform(get("/api/reportes/"+tipo).header("Authorization",token).param("desde","2026-05-01").param("hasta","2026-05-31"));
    }
    private long comprobante(int manoObra) throws Exception {
        return crear("/api/comprobantes","{\"vehiculoId\":"+vehiculo+",\"descripcion\":\"Prueba\",\"manoObra\":"+manoObra+",\"porcentajeIva\":21,\"items\":[]}");
    }
    private void cobrar(long id,String medio,String instante) throws Exception {
        enviar("/api/comprobantes/"+id+"/cobro","{\"medioPago\":\""+medio+"\"}").andExpect(status().isOk());
        jdbc.update("UPDATE octano.cobro SET fecha=? WHERE comprobante_id=?",Timestamp.from(Instant.parse(instante)),id);
    }
    private long orden(String tipos) throws Exception {
        return leer(enviar("/api/ordenes-trabajo","{\"vehiculoId\":"+vehiculo+",\"mecanicoId\":"+mecanico+",\"descripcion\":\"Trabajo reportes\",\"tiposTrabajo\":"+tipos+",\"manoObra\":100,\"porcentajeIva\":21}")
                .andExpect(status().isCreated())).get("orden").get("id").asLong();
    }
    private void finalizar(long orden,String fecha) throws Exception {
        var iniciada=leer(enviar("/api/ordenes-trabajo/"+orden+"/inicio","").andExpect(status().isOk()));
        String aceite=iniciada.get("orden").get("tiposTrabajo").toString().contains("SERVICE")?"5W30":"";
        enviar("/api/ordenes-trabajo/"+orden+"/finalizacion","{\"kilometraje\":1000,\"aceite\":\""+aceite+"\",\"filtros\":[]}").andExpect(status().isOk());
        jdbc.update("UPDATE octano.orden_trabajo SET fin_fecha=? WHERE id=?",Timestamp.from(Instant.parse(fecha)),orden);
    }
    @Test void unTallerSinActividadDevuelveCerosYDiasSinIngresos() throws Exception {
        // Sin datos no hay null, divisiones por cero ni días que desaparecen del gráfico.
        var ingresos=leer(reporte("ingresos").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.cantidadCobros").value(0)).andExpect(jsonPath("$.promedioPorCobro").value(0)));
        assertEquals(31,ingresos.get("porDia").size());
        assertEquals("2026-05-01",ingresos.get("porDia").get(0).get("fecha").asString());
        assertEquals("2026-05-31",ingresos.get("porDia").get(30).get("fecha").asString());
        for(var dia:ingresos.get("porDia")) assertEquals(0,dia.get("cantidad").asLong());
        reporte("trabajos").andExpect(status().isOk()).andExpect(jsonPath("$.creadas").value(0)).andExpect(jsonPath("$.finalizadas").value(0));
        reporte("repuestos").andExpect(status().isOk()).andExpect(jsonPath("$.totalStockBajo").value(0));
        reporte("resumen").andExpect(status().isOk()).andExpect(jsonPath("$.ingresos").value(0)).andExpect(jsonPath("$.comprobantesPorCobrar.importe").value(0));
    }
    @Test void cuentaCobrosRealesConFechasArgentinasYNoCuentaBorradoresNiImpagos() throws Exception {
        // Los límites del mes son locales: las 03:00 UTC son las 00:00 de Argentina.
        cobrar(comprobante(100),"EFECTIVO","2026-05-01T02:59:59Z");
        cobrar(comprobante(200),"EFECTIVO","2026-05-01T03:00:00Z");
        cobrar(comprobante(300),"TRANSFERENCIA","2026-06-01T02:59:59Z");
        cobrar(comprobante(400),"EFECTIVO","2026-06-01T03:00:00Z");
        enviar("/api/comprobantes/"+comprobante(500)+"/emision","").andExpect(status().isOk());
        comprobante(600);
        reporte("ingresos").andExpect(status().isOk()).andExpect(jsonPath("$.cantidadCobros").value(2))
                .andExpect(jsonPath("$.total").value(605)).andExpect(jsonPath("$.promedioPorCobro").value(302.5))
                .andExpect(jsonPath("$.porDia[0].importe").value(242)).andExpect(jsonPath("$.porDia[30].importe").value(363))
                .andExpect(jsonPath("$.porDia[1].importe").value(0)).andExpect(jsonPath("$.porMedioPago.length()").value(2));
        reporte("resumen").andExpect(jsonPath("$.ingresos").value(605)).andExpect(jsonPath("$.comprobantesPorCobrar.cantidad").value(1))
                .andExpect(jsonPath("$.comprobantesPorCobrar.importe").value(605));
    }
    @Test void separaLasOrdenesCreadasDeLasFinalizadasYNoDuplicaUnaOrdenConDosTipos() throws Exception {
        // Una OT puede cerrar en otro mes y tener dos tipos de trabajo, pero sigue siendo una sola OT.
        long cerrada=orden("[\"SERVICE\",\"DISTRIBUCION\"]");
        enviar("/api/ordenes-trabajo/"+cerrada+"/repuestos","{\"items\":[{\"descripcion\":\"Concepto 1\",\"cantidad\":1,\"precioUnitario\":10},{\"descripcion\":\"Concepto 2\",\"cantidad\":1,\"precioUnitario\":20}]}").andExpect(status().isOk());
        finalizar(cerrada,"2026-05-01T03:00:00Z");
        jdbc.update("UPDATE octano.orden_trabajo SET fecha_creacion='2026-04-30 12:00:00+00' WHERE id=?",cerrada);
        long espera=orden("[\"OTRO\"]"),cancelada=orden("[\"OTRO\"]"),fuera=orden("[\"OTRO\"]");
        enviar("/api/ordenes-trabajo/"+cancelada+"/cancelacion","{\"motivo\":\"Prueba\"}").andExpect(status().isOk());
        finalizar(fuera,"2026-06-01T03:00:00Z");
        for(long id:List.of(espera,cancelada,fuera)) jdbc.update("UPDATE octano.orden_trabajo SET fecha_creacion='2026-05-05 12:00:00+00' WHERE id=?",id);
        jdbc.update("UPDATE octano.mecanico SET activo=false WHERE id=?",mecanico);
        reporte("trabajos").andExpect(status().isOk()).andExpect(jsonPath("$.creadas").value(3))
                .andExpect(jsonPath("$.finalizadas").value(1)).andExpect(jsonPath("$.finalizadasPorTipo.length()").value(2))
                .andExpect(jsonPath("$.finalizadasPorTipo[0].cantidad").value(1)).andExpect(jsonPath("$.finalizadasPorTipo[1].cantidad").value(1))
                .andExpect(jsonPath("$.finalizadasPorMecanico[0].mecanicoId").value(mecanico))
                .andExpect(jsonPath("$.finalizadasPorMecanico[0].nombre").value("Juan Perez"))
                .andExpect(jsonPath("$.finalizadasPorMecanico[0].finalizadas").value(1));
    }
    private long repuesto(String codigo) throws Exception {
        return crear("/api/repuestos","{\"codigoOem\":\""+codigo+"\",\"descripcion\":\"Repuesto reportes\",\"marca\":\"Prueba\",\"precioCosto\":100,\"margen\":25,\"stockMinimo\":2}");
    }
    private void mover(long id,String tipo,String cantidad,String fecha) throws Exception {
        long movimiento=crear("/api/repuestos/"+id+"/movimientos","{\"tipo\":\""+tipo+"\",\"cantidad\":"+cantidad+",\"motivo\":\"Prueba reporte\"}");
        jdbc.update("UPDATE octano.movimiento_stock SET fecha=? WHERE id=?",Timestamp.from(Instant.parse(fecha)),movimiento);
    }
    @Test void reportaMovimientosRealesIncluyendoDevolucionesYAjustesYConservaElHistorialInactivo() throws Exception {
        // Las devoluciones son entradas; un ajuste no se presenta como una venta ni como un consumo.
        long id=repuesto("REP-001");
        mover(id,"ENTRADA","10.5","2026-05-02T12:00:00Z");
        mover(id,"SALIDA","3.25","2026-05-03T12:00:00Z");
        mover(id,"ENTRADA","1.25","2026-05-04T12:00:00Z");
        mover(id,"AJUSTE","7","2026-05-05T12:00:00Z");
        jdbc.update("UPDATE octano.repuesto SET activo=false WHERE id=?",id);
        long bajo=repuesto("REP-002"); mover(bajo,"ENTRADA","2","2026-04-01T12:00:00Z");
        repuesto("REP-003");
        reporte("repuestos").andExpect(status().isOk()).andExpect(jsonPath("$.movimientosPorRepuesto.length()").value(1))
                .andExpect(jsonPath("$.movimientosPorRepuesto[0].repuestoId").value(id)).andExpect(jsonPath("$.movimientosPorRepuesto[0].activo").value(false))
                .andExpect(jsonPath("$.movimientosPorRepuesto[0].entradas").value(11.75)).andExpect(jsonPath("$.movimientosPorRepuesto[0].salidas").value(3.25))
                .andExpect(jsonPath("$.movimientosPorRepuesto[0].ajusteNeto").value(-1.5)).andExpect(jsonPath("$.movimientosPorRepuesto[0].variacionNeta").value(7))
                .andExpect(jsonPath("$.totalStockBajo").value(2));
        api.perform(get("/api/reportes/repuestos").header("Authorization",token).param("limite","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalStockBajo").value(2)).andExpect(jsonPath("$.stockBajoActual.length()").value(1));
    }
    @Test void elResumenDistingueVehiculosDeOrdenesYLaSituacionActualDelPeriodo() throws Exception {
        // Dos órdenes del mismo auto cuentan como un vehículo; el rango no oculta trabajos actuales.
        long primera=orden("[\"OTRO\"]"); orden("[\"OTRO\"]");
        enviar("/api/ordenes-trabajo/"+primera+"/inicio","").andExpect(status().isOk());
        long finalizada=orden("[\"OTRO\"]"); finalizar(finalizada,"2026-05-10T12:00:00Z");
        reporte("resumen").andExpect(status().isOk()).andExpect(jsonPath("$.operacionActual.vehiculosEnTaller").value(1))
                .andExpect(jsonPath("$.operacionActual.ordenesEnEspera").value(1)).andExpect(jsonPath("$.operacionActual.ordenesEnProceso").value(1))
                .andExpect(jsonPath("$.operacionActual.ordenesFinalizadasSinComprobante").value(1));
        crear("/api/comprobantes/desde-orden/"+finalizada,"");
        reporte("resumen").andExpect(jsonPath("$.operacionActual.ordenesFinalizadasSinComprobante").value(0));
    }
    @Test void losTurnosDelResumenUsanElDiaActualYExcluyenCancelados() throws Exception {
        // La agenda se cuenta por el día argentino real; los turnos cancelados no se suman.
        LocalDate hoy=LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        long activo=crear("/api/turnos","{\"clienteId\":"+cliente+",\"vehiculoId\":"+vehiculo+",\"fecha\":\""+hoy.plusDays(1)+"\",\"horaInicio\":\"10:00\",\"horaFin\":\"11:00\",\"motivoVisita\":\"Prueba\"}");
        long cancelado=crear("/api/turnos","{\"clienteId\":"+cliente+",\"vehiculoId\":"+vehiculo+",\"fecha\":\""+hoy.plusDays(2)+"\",\"horaInicio\":\"10:00\",\"horaFin\":\"11:00\",\"motivoVisita\":\"Prueba\"}");
        jdbc.update("UPDATE octano.turno SET estado='CANCELADO',fecha=? WHERE id=?",hoy,cancelado);
        jdbc.update("UPDATE octano.turno SET fecha=? WHERE id=?",hoy,activo);
        reporte("resumen").andExpect(status().isOk()).andExpect(jsonPath("$.hoy").value(hoy.toString()))
                .andExpect(jsonPath("$.operacionActual.turnosHoy").value(1));
    }
    @Test void losReportesSoloLeenElTallerDeLaSesion() throws Exception {
        // Enviar un tallerId ajeno no permite elegir otra empresa.
        cobrar(comprobante(100),"EFECTIVO","2026-05-10T12:00:00Z"); orden("[\"OTRO\"]"); repuesto("AISLADO");
        var otro=cuenta("otro@reportes.test"); token=otro.bearer();
        for(String tipo:List.of("ingresos","resumen","trabajos","repuestos")) {
            var respuesta=leer(api.perform(get("/api/reportes/"+tipo).header("Authorization",token).param("tallerId",admin.tallerId()+"")
                    .param("desde","2026-05-01").param("hasta","2026-05-31")).andExpect(status().isOk()));
            if(tipo.equals("ingresos")) assertEquals(0,respuesta.get("cantidadCobros").asLong());
            if(tipo.equals("resumen")) assertEquals(0,respuesta.get("operacionActual").get("vehiculosEnTaller").asLong());
            if(tipo.equals("trabajos")) assertEquals(0,respuesta.get("creadas").asLong());
            if(tipo.equals("repuestos")) assertEquals(0,respuesta.get("totalStockBajo").asLong());
        }
    }
    @Test void validaFechasLimitesYUsaElMesActualPorDefecto() throws Exception {
        // Las fechas invertidas, formatos incorrectos y períodos demasiado grandes devuelven 400.
        for(String tipo:List.of("resumen","ingresos","trabajos","repuestos")) {
            api.perform(get("/api/reportes/"+tipo).header("Authorization",token).param("desde","2026-06-01").param("hasta","2026-05-01"))
                    .andExpect(status().isBadRequest());
            api.perform(get("/api/reportes/"+tipo).header("Authorization",token).param("desde","2025-01-01").param("hasta","2026-12-31"))
                    .andExpect(status().isBadRequest());
            api.perform(get("/api/reportes/"+tipo).header("Authorization",token).param("desde","fecha"))
                    .andExpect(status().isBadRequest());
        }
        LocalDate hoy=LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        api.perform(get("/api/reportes/ingresos").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.desde").value(hoy.withDayOfMonth(1).toString())).andExpect(jsonPath("$.periodo.hasta").value(hoy.toString()));
        api.perform(get("/api/reportes/ingresos").header("Authorization",token).param("hasta","2026-05-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.porDia.length()").value(1));
        for(String limite:List.of("0","101","texto")) api.perform(get("/api/reportes/repuestos").header("Authorization",token).param("limite",limite)).andExpect(status().isBadRequest());
    }
    @Test void soloAdministracionConsultaReportes() throws Exception {
        // Los datos económicos no quedan accesibles para recepción ni para mecánicos.
        crear("/api/usuarios","{\"email\":\"recepcion@reportes.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"RECEPCION\"}");
        crear("/api/usuarios","{\"email\":\"mecanico@reportes.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"MECANICO\",\"mecanicoId\":"+mecanico+"}");
        for(String email:List.of("recepcion@reportes.test","mecanico@reportes.test")) {
            var login=leer(enviar("/api/auth/login","{\"email\":\""+email+"\",\"password\":\"ClaveDePrueba123!\"}").andExpect(status().isOk()));
            token="Bearer "+login.get("tokenAcceso").asString();
            for(String tipo:List.of("resumen","ingresos","trabajos","repuestos")) reporte(tipo).andExpect(status().isForbidden());
        }
        for(String tipo:List.of("resumen","ingresos","trabajos","repuestos")) api.perform(get("/api/reportes/"+tipo)).andExpect(status().isUnauthorized());
    }
}
