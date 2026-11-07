package com.octano.backend.comprobantes;

import com.octano.backend.soporte.ApiFixture;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class ComprobanteApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    private final JsonMapper json=new JsonMapper();
    private final List<Long> talleres=new ArrayList<>();
    private ApiFixture.Cuenta admin;
    private String token;
    private long condicion,marca,modelo,cliente,vehiculo,mecanico,repuesto;

    @BeforeEach void preparar() throws Exception {
        condicion=jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva",Long.class);
        admin=cuenta("admin@comprobantes.test"); token=admin.bearer();
        cliente=crear("/api/clientes",cliente("Cliente original","12345678"));
        marca=ApiFixture.marca(jdbc,"Marca comprobantes"); modelo=ApiFixture.modelo(jdbc,marca,"Modelo comprobantes");
        vehiculo=crear("/api/vehiculos","{\"clienteId\":"+cliente+",\"modeloId\":"+modelo+",\"patente\":\"abc123\",\"anio\":2020}");
        mecanico=crear("/api/mecanicos","{\"nombre\":\"Juan\",\"apellido\":\"Perez\",\"telefono\":\"02392 456789\",\"fechaInicio\":\"2020-01-01\"}");
        repuesto=crear("/api/repuestos","{\"codigoOem\":\"FAC-001\",\"descripcion\":\"Filtro de prueba\",\"marca\":\"Prueba\",\"precioCosto\":100,\"margen\":25,\"stockMinimo\":0}");
        enviar("/api/repuestos/"+repuesto+"/movimientos","{\"tipo\":\"ENTRADA\",\"cantidad\":5,\"motivo\":\"Prueba\"}").andExpect(status().isCreated());
    }
    private ApiFixture.Cuenta cuenta(String email) throws Exception {
        var c=ApiFixture.registrar(api,email,condicion); talleres.add(c.tallerId()); return c;
    }
    @AfterEach void limpiar() {
        // Confirmamos cada petición como en producción y luego borramos solo los talleres temporales de esta prueba.
        new TransactionTemplate(transacciones).executeWithoutResult(estado->{
            for(long taller:talleres) {
                for(String tabla:List.of("cobro","detalle_comprobante","comprobante","codigo_qr","foto_orden","tarea_orden","repuesto_orden",
                        "orden_trabajo","turno","detalle_presupuesto","presupuesto","compatibilidad_repuesto","movimiento_stock","repuesto",
                        "historial_titularidad","vehiculo","cliente","usuario","mecanico")) jdbc.update("DELETE FROM octano."+tabla+" WHERE taller_id=?",taller);
                jdbc.update("DELETE FROM octano.taller WHERE id=?",taller);
            }
            if(modelo!=0) jdbc.update("DELETE FROM octano.modelo WHERE id=?",modelo);
            if(marca!=0) jdbc.update("DELETE FROM octano.marca WHERE id=?",marca);
        });
    }
    private String cliente(String nombre,String documento) {
        return "{\"nombre\":\""+nombre+"\",\"documento\":\""+documento+"\",\"telefono\":\"02392 456789\",\"calle\":\"Calle original\",\"altura\":100,\"condicionIvaId\":"+condicion+"}";
    }
    private ResultActions enviar(String ruta,String cuerpo) throws Exception {
        return api.perform(post(ruta).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
    private JsonNode leer(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }
    private long crear(String ruta,String cuerpo) throws Exception { return leer(enviar(ruta,cuerpo).andExpect(status().isCreated())).get("id").asLong(); }
    private String ruta(long id) { return "/api/comprobantes/"+id; }
    private String datos(Long orden,String items) {
        return """
                {"ordenId":%s,"vehiculoId":%d,"descripcion":"Trabajo interno","manoObra":50,"porcentajeIva":21,"items":%s}
                """.formatted(orden,vehiculo,items);
    }
    private String items() { return "[{\"repuestoId\":"+repuesto+",\"cantidad\":2},{\"descripcion\":\"Insumo manual\",\"cantidad\":1.5,\"precioUnitario\":10.10}]"; }
    private long borrador() throws Exception { return crear("/api/comprobantes",datos(null,items())); }
    private ResultActions consultar(long id) throws Exception { return api.perform(get(ruta(id)).header("Authorization",token)); }
    private String pago(String medio) { return "{\"medioPago\":\""+medio+"\",\"observaciones\":\" Pago de prueba \"}"; }
    private ResultActions cobrar(long id) throws Exception { return enviar(ruta(id)+"/cobro",pago("EFECTIVO")); }
    private long orden(Long presupuesto) throws Exception {
        var respuesta=leer(enviar("/api/ordenes-trabajo","""
                {"vehiculoId":%d,"presupuestoId":%s,"mecanicoId":%d,"descripcion":"Trabajo de orden",
                "tiposTrabajo":["OTRO"],"manoObra":50,"porcentajeIva":21}
                """.formatted(vehiculo,presupuesto,mecanico)).andExpect(status().isCreated()));
        return respuesta.get("orden").get("id").asLong();
    }
    private void finalizar(long id) throws Exception {
        enviar("/api/ordenes-trabajo/"+id+"/inicio","").andExpect(status().isOk());
        enviar("/api/ordenes-trabajo/"+id+"/finalizacion","{\"kilometraje\":1000,\"filtros\":[]}").andExpect(status().isOk());
    }
    private long movimientos() { return jdbc.queryForObject("SELECT count(*) FROM octano.movimiento_stock WHERE repuesto_id=?",Long.class,repuesto); }

    @Test void guardaBorradorYCalculaImportesEnElServidor() throws Exception {
        // El total se calcula con los materiales y la mano de obra, ignorando totales enviados desde el navegador.
        String cuerpo=datos(null,items()).replace("\"manoObra\":50","\"manoObra\":50,\"total\":1,\"tipo\":\"A\",\"esFiscal\":true");
        long id=crear("/api/comprobantes",cuerpo);
        consultar(id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.tipo").value("X")).andExpect(jsonPath("$.esFiscal").value(false))
                .andExpect(jsonPath("$.numero").isEmpty()).andExpect(jsonPath("$.totalRepuestos").value(265.15))
                .andExpect(jsonPath("$.subtotal").value(315.15)).andExpect(jsonPath("$.montoIva").value(66.18))
                .andExpect(jsonPath("$.total").value(381.33)).andExpect(jsonPath("$.items[0].precioUnitario").value(125))
                .andExpect(jsonPath("$.creadoPor").value(admin.usuarioId())).andExpect(jsonPath("$.receptor.nombre").value("Cliente original"));
        assertEquals(1L,movimientos());
    }

    @Test void editaSoloElBorradorYRedondeaCadaRenglon() throws Exception {
        // Cada medio centavo se redondea antes de sumar. Emitir congela la edición.
        long id=borrador();
        String items="[{\"descripcion\":\"A\",\"cantidad\":0.05,\"precioUnitario\":0.10},{\"descripcion\":\"B\",\"cantidad\":0.05,\"precioUnitario\":0.10}]";
        api.perform(put(ruta(id)).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(datos(null,items)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRepuestos").value(0.02)).andExpect(jsonPath("$.total").value(60.52));
        enviar(ruta(id)+"/emision","").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EMITIDO"));
        api.perform(put(ruta(id)).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(datos(null,items)))
                .andExpect(status().isBadRequest());
    }

    @Test void registraAlUltimoUsuarioQueModificoElComprobante() throws Exception {
        // Administración crea y recepción edita: se conserva el creador y cambia el último editor.
        long id=borrador();
        var original=leer(consultar(id).andExpect(status().isOk()));
        long recepcion=crear("/api/usuarios","{\"email\":\"editor@comprobantes.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"RECEPCION\"}");
        token=login("editor@comprobantes.test");
        // El usuario y la fecha salen de la sesión y del servidor, aunque se envíen otros en el JSON.
        String solicitud=datos(null,items()).replace("\"manoObra\":50",
                "\"manoObra\":60,\"usuarioModificacionId\":"+admin.usuarioId()+",\"fechaModif\":\"2000-01-01T00:00:00Z\"");
        var editado=leer(api.perform(put(ruta(id)).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioModificacionId").value(recepcion))
                .andExpect(jsonPath("$.creadoPor").value(admin.usuarioId())).andExpect(jsonPath("$.manoObra").value(60)));
        assertEquals(original.get("creadoEn"),editado.get("creadoEn"));
        assertTrue(java.time.Instant.parse(editado.get("fechaModif").asString())
                .isAfter(java.time.Instant.parse(original.get("fechaModif").asString())));
        consultar(id).andExpect(jsonPath("$.usuarioModificacionId").value(recepcion))
                .andExpect(jsonPath("$.fechaModif").value(editado.get("fechaModif").asString()));
        assertEquals(recepcion,jdbc.queryForObject("SELECT usuario_modificacion_id FROM octano.comprobante WHERE id=?",Long.class,id));

        // Emitir y cobrar también registran quién hizo cada operación.
        token=admin.bearer();
        enviar(ruta(id)+"/emision","").andExpect(status().isOk()).andExpect(jsonPath("$.emitidoPor").value(admin.usuarioId()))
                .andExpect(jsonPath("$.usuarioModificacionId").value(admin.usuarioId()));
        token=login("editor@comprobantes.test");
        var cobrado=leer(cobrar(id).andExpect(status().isOk()).andExpect(jsonPath("$.cobro.usuarioId").value(recepcion))
                .andExpect(jsonPath("$.usuarioModificacionId").value(recepcion)).andExpect(jsonPath("$.creadoPor").value(admin.usuarioId())));
        token=admin.bearer();
        api.perform(put(ruta(id)).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(datos(null,items())))
                .andExpect(status().isBadRequest());
        // Una edición rechazada no cambia el último usuario ni la fecha guardados.
        consultar(id).andExpect(jsonPath("$.usuarioModificacionId").value(recepcion))
                .andExpect(jsonPath("$.fechaModif").value(cobrado.get("fechaModif").asString()));
    }

    @Test void copiaLaOrdenSinDuplicarMaterialesDelPresupuestoNiDescontarStock() throws Exception {
        // La cantidad real se ajusta sobre el renglón presupuestado; el comprobante lo copia una sola vez.
        long presupuesto=crear("/api/presupuestos","""
                {"clienteId":%d,"vehiculoId":%d,"descripcion":"Reparación","manoObra":25,"porcentajeIva":21,
                "tiposTrabajo":["OTRO"],"items":[{"repuestoId":%d,"cantidad":2,"precioUnitario":90}]}
                """.formatted(cliente,vehiculo,repuesto));
        long orden=orden(presupuesto);
        var detalle=leer(api.perform(get("/api/ordenes-trabajo/"+orden).header("Authorization",token)).andExpect(status().isOk()));
        long renglon=detalle.get("items").get(0).get("id").asLong();
        api.perform(patch("/api/ordenes-trabajo/"+orden+"/repuestos/"+renglon).header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cantidad\":3}")).andExpect(status().isOk());
        enviar("/api/ordenes-trabajo/"+orden+"/repuestos","{\"items\":[{\"descripcion\":\"Insumo adicional\",\"cantidad\":1,\"precioUnitario\":125}]}").andExpect(status().isOk());
        finalizar(orden); long movimientos=movimientos();
        jdbc.update("UPDATE octano.repuesto SET activo=FALSE,precio_publico=999 WHERE id=?",repuesto);
        long id=crear("/api/comprobantes/desde-orden/"+orden,"");
        consultar(id).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].precioUnitario").value(90))
                .andExpect(jsonPath("$.items[0].cantidad").value(3)).andExpect(jsonPath("$.manoObra").value(25)).andExpect(jsonPath("$.total").value(508.2));
        cobrar(id).andExpect(status().isOk());
        assertEquals(movimientos,movimientos());
        assertEquals(0,new BigDecimal("2").compareTo(jdbc.queryForObject("SELECT stock FROM octano.repuesto WHERE id=?",BigDecimal.class,repuesto)));
        enviar("/api/comprobantes/desde-orden/"+orden,"").andExpect(status().isBadRequest());
    }

    @Test void rechazaOrdenesSinFinalizarYVinculosIncorrectos() throws Exception {
        // Una orden pendiente o de otro vehículo no se puede facturar.
        long orden=orden(null);
        enviar("/api/comprobantes/desde-orden/"+orden,"").andExpect(status().isBadRequest());
        enviar("/api/comprobantes/desde-orden/"+orden+"/cobro",pago("EFECTIVO")).andExpect(status().isBadRequest());
        finalizar(orden);
        long otroVehiculo=crear("/api/vehiculos","{\"clienteId\":"+cliente+",\"modeloId\":"+modelo+",\"patente\":\"xyz987\",\"anio\":2020}");
        enviar("/api/comprobantes",datos(orden,"[]").replace("\"vehiculoId\":"+vehiculo,"\"vehiculoId\":"+otroVehiculo)).andExpect(status().isBadRequest());
        long id=crear("/api/comprobantes/desde-orden/"+orden,"");
        api.perform(put(ruta(id)).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(datos(null,"[]"))).andExpect(status().isBadRequest());
    }

    @Test void cobrarEmiteElBorradorYGuardaElImporteRealYElUsuario() throws Exception {
        // Un cobro usa el total guardado y registra fecha, medio, nota y usuario de la sesión.
        long id=borrador();
        enviar(ruta(id)+"/cobro",pago("TRANSFERENCIA").replace("{","{\"importe\":1,"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COBRADO"))
                .andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.cobro.importe").value(381.33))
                .andExpect(jsonPath("$.cobro.usuarioId").value(admin.usuarioId())).andExpect(jsonPath("$.cobro.fecha").isNotEmpty())
                .andExpect(jsonPath("$.cobro.observaciones").value("Pago de prueba"));
        cobrar(id).andExpect(status().isBadRequest());
        api.perform(get(ruta(id)+"/cobro").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.medioPago").value("TRANSFERENCIA"));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM octano.cobro WHERE comprobante_id=?",Long.class,id));
    }

    @Test void elCobroDirectoDeLaOrdenEsUnaSolaOperacion() throws Exception {
        // Sin borrador previo, el mismo botón crea el comprobante y registra el cobro completo.
        long orden=orden(null); finalizar(orden);
        var resultado=leer(enviar("/api/comprobantes/desde-orden/"+orden+"/cobro",pago("EFECTIVO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COBRADO")).andExpect(jsonPath("$.total").value(60.5)));
        enviar("/api/comprobantes/desde-orden/"+orden+"/cobro",pago("EFECTIVO")).andExpect(status().isBadRequest());
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM octano.comprobante WHERE orden_id=?",Long.class,orden));
        consultar(resultado.get("id").asLong()).andExpect(jsonPath("$.ordenId").value(orden));
    }

    @Test void conservaLasCopiasEmitidasAunqueCambienElClienteElTallerYElVehiculo() throws Exception {
        // La emisión guarda los datos actuales; los cambios posteriores no reescriben el documento.
        long id=borrador();
        api.perform(put("/api/clientes/"+cliente).header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content(cliente("Cliente al emitir","12345678"))).andExpect(status().isOk());
        enviar(ruta(id)+"/emision","").andExpect(status().isOk()).andExpect(jsonPath("$.receptor.nombre").value("Cliente al emitir"));
        jdbc.update("UPDATE octano.cliente SET nombre='Nombre posterior' WHERE id=?",cliente);
        jdbc.update("UPDATE octano.taller SET nombre='Taller posterior' WHERE id=?",admin.tallerId());
        jdbc.update("UPDATE octano.vehiculo SET patente='XYZ-987' WHERE id=?",vehiculo);
        consultar(id).andExpect(jsonPath("$.receptor.nombre").value("Cliente al emitir"))
                .andExpect(jsonPath("$.emisor.nombre").value("Taller de prueba")).andExpect(jsonPath("$.vehiculo.patente").value("ABC-123"));
        cobrar(id).andExpect(status().isOk()).andExpect(jsonPath("$.receptor.nombre").value("Cliente al emitir"));
    }

    @Test void conservaElClienteDeLaOrdenDespuesDeTransferirElVehiculo() throws Exception {
        // El trabajo sigue perteneciendo a quien lo pidió, aunque el auto tenga otro titular al cobrar.
        long orden=orden(null); finalizar(orden);
        long nuevo=crear("/api/clientes",cliente("Nuevo titular","23456789"));
        api.perform(patch("/api/vehiculos/"+vehiculo+"/titular").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":"+nuevo+"}")).andExpect(status().isOk());
        long id=crear("/api/comprobantes/desde-orden/"+orden,"");
        cobrar(id).andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(cliente))
                .andExpect(jsonPath("$.receptor.nombre").value("Cliente original"));
    }

    @Test void filtraBorradoresEmitidosCobradosYPendientesDePreparar() throws Exception {
        // Las órdenes sin comprobante aparecen pendientes; después, se consultan por su estado comercial.
        long orden=orden(null); finalizar(orden);
        api.perform(get("/api/comprobantes/ordenes-pendientes").header("Authorization",token).param("q","Cliente original").param("conPresupuesto","false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].ordenId").value(orden));
        long borrador=crear("/api/comprobantes/desde-orden/"+orden,"");
        api.perform(get("/api/comprobantes/ordenes-pendientes").header("Authorization",token)).andExpect(jsonPath("$.totalElementos").value(0));
        api.perform(get(ruta(borrador)+"/cobro").header("Authorization",token)).andExpect(status().isNotFound());
        enviar(ruta(borrador)+"/emision","").andExpect(status().isOk());
        for(String estado:List.of("EMITIDO","BORRADOR","COBRADO"))
            api.perform(get("/api/comprobantes").header("Authorization",token).param("estado",estado))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(estado.equals("EMITIDO")?1:0));
        cobrar(borrador).andExpect(status().isOk());
        api.perform(get("/api/cobros").header("Authorization",token).param("medioPago","EFECTIVO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        api.perform(get("/api/comprobantes").header("Authorization",token).param("q","ABC-123").param("estado","COBRADO"))
                .andExpect(jsonPath("$.contenido[0].id").value(borrador));
    }

    @Test void validaImportesMediosDePagoYPaginacion() throws Exception {
        // Un comprobante debe tener un total positivo y datos válidos; no aceptamos medios desconocidos.
        for(String cuerpo:List.of("{}",datos(null,"[]").replace("\"manoObra\":50","\"manoObra\":0"),
                datos(null,"[]").replace("\"manoObra\":50","\"manoObra\":-1"),datos(null,"[]").replace("\"porcentajeIva\":21","\"porcentajeIva\":101"),
                datos(null,"[{\"descripcion\":\"A\",\"cantidad\":0,\"precioUnitario\":1}]"),datos(null,"[{\"cantidad\":1}]")))
            enviar("/api/comprobantes",cuerpo).andExpect(status().isBadRequest());
        long id=borrador();
        enviar(ruta(id)+"/cobro","{}").andExpect(status().isBadRequest());
        enviar(ruta(id)+"/cobro",pago("OTRO")).andExpect(status().isBadRequest());
        consultar(id).andExpect(jsonPath("$.estado").value("BORRADOR")).andExpect(jsonPath("$.numero").isEmpty());
        api.perform(get("/api/comprobantes").header("Authorization",token).param("tamano","101")).andExpect(status().isBadRequest());
        api.perform(get("/api/cobros").header("Authorization",token).param("desde","2026-12-01").param("hasta","2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test void admiteLosCincoMediosDePagoDelFrontend() throws Exception {
        // Cada opción del selector del frontend tiene su valor equivalente en la API.
        for(String medio:List.of("EFECTIVO","TRANSFERENCIA","TARJETA_DEBITO","TARJETA_CREDITO","MERCADO_PAGO")) {
            long id=crear("/api/comprobantes",datos(null,"[]"));
            enviar(ruta(id)+"/cobro",pago(medio)).andExpect(status().isOk()).andExpect(jsonPath("$.cobro.medioPago").value(medio));
        }
    }

    @Test void otroTallerNoPuedeConsultarModificarEmitirNiCobrar() throws Exception {
        // Conocer el ID ajeno no permite acceder a sus comprobantes o cobros.
        long id=borrador(); var otro=cuenta("otro@comprobantes.test"); token=otro.bearer();
        consultar(id).andExpect(status().isNotFound()); cobrar(id).andExpect(status().isNotFound());
        enviar(ruta(id)+"/emision","").andExpect(status().isNotFound());
        api.perform(put(ruta(id)).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(datos(null,"[]")))
                .andExpect(status().isNotFound());
        api.perform(get("/api/comprobantes").header("Authorization",token)).andExpect(jsonPath("$.totalElementos").value(0));
        api.perform(get("/api/cobros").header("Authorization",token)).andExpect(jsonPath("$.totalElementos").value(0));
        api.perform(get("/api/comprobantes")).andExpect(status().isUnauthorized());
    }

    @Test void recepcionPuedeCobrarYElMecanicoNoAccede() throws Exception {
        // Se registra el usuario de recepción que cobró; el mecánico no gestiona facturación.
        long id=borrador();
        long usuario=crear("/api/usuarios","{\"email\":\"recepcion@comprobantes.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"RECEPCION\"}");
        token=login("recepcion@comprobantes.test");
        cobrar(id).andExpect(status().isOk()).andExpect(jsonPath("$.cobro.usuarioId").value(usuario));
        token=admin.bearer();
        crear("/api/usuarios","{\"email\":\"mecanico@comprobantes.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"MECANICO\",\"mecanicoId\":"+mecanico+"}");
        token=login("mecanico@comprobantes.test");
        consultar(id).andExpect(status().isForbidden()); cobrar(id).andExpect(status().isForbidden());
        api.perform(get("/api/cobros").header("Authorization",token)).andExpect(status().isForbidden());
    }
    private String login(String email) throws Exception {
        var r=leer(api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"password\":\"ClaveDePrueba123!\"}")).andExpect(status().isOk()));
        return "Bearer "+r.get("tokenAcceso").asString();
    }

    @Test void dosPeticionesSimultaneasNoCobranDosVeces() throws Exception {
        // Dos clics al mismo tiempo dejan un único cobro y un único número interno.
        long id=borrador(); var inicio=new CountDownLatch(1);
        try(var ejecutor=Executors.newFixedThreadPool(2)) {
            var a=ejecutor.submit(()->{inicio.await(5,TimeUnit.SECONDS);return cobrar(id).andReturn().getResponse().getStatus();});
            var b=ejecutor.submit(()->{inicio.await(5,TimeUnit.SECONDS);return cobrar(id).andReturn().getResponse().getStatus();});
            inicio.countDown();
            assertEquals(List.of(200,400),List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM octano.cobro WHERE comprobante_id=?",Long.class,id));
    }

    @Test void dosEmisionesSimultaneasRecibenNumerosDistintos() throws Exception {
        // La numeración es propia de cada taller y no se repite cuando dos usuarios emiten a la vez.
        long primero=borrador(),segundo=borrador(); var inicio=new CountDownLatch(1);
        try(var ejecutor=Executors.newFixedThreadPool(2)) {
            var a=ejecutor.submit(()->{inicio.await(5,TimeUnit.SECONDS);return leer(enviar(ruta(primero)+"/emision","").andExpect(status().isOk())).get("numero").asLong();});
            var b=ejecutor.submit(()->{inicio.await(5,TimeUnit.SECONDS);return leer(enviar(ruta(segundo)+"/emision","").andExpect(status().isOk())).get("numero").asLong();});
            inicio.countDown();
            assertEquals(List.of(1L,2L),List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)).stream().sorted().toList());
        }
        long numero=leer(consultar(primero)).get("numero").asLong();
        enviar(ruta(primero)+"/emision","").andExpect(status().isOk()).andExpect(jsonPath("$.numero").value(numero));
    }
}
