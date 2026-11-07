package com.octano.backend.codigosqr;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.octano.backend.soporte.ApiFixture;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
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
class QrApiTests {
    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    private final JsonMapper json = new JsonMapper();
    private final List<Long> talleres = new ArrayList<>();
    private ApiFixture.Cuenta admin;
    private String token;
    private long condicion,marca,modelo,cliente,vehiculo;

    @BeforeEach void preparar() throws Exception {
        condicion=jdbc.queryForObject("SELECT min(id) FROM octano.condicion_iva",Long.class);
        admin=cuenta("admin@qr.test"); token=admin.bearer();
        cliente=crear("/api/clientes","{\"nombre\":\"Cliente privado\",\"documento\":\"12345678\",\"telefono\":\"02392 456789\",\"email\":\"cliente@privado.test\",\"condicionIvaId\":"+condicion+"}");
        marca=ApiFixture.marca(jdbc,"Marca QR"); modelo=ApiFixture.modelo(jdbc,marca,"Modelo QR");
        vehiculo=crearVehiculo("abc123");
    }
    private ApiFixture.Cuenta cuenta(String email) throws Exception {
        var cuenta=ApiFixture.registrar(api,email,condicion); talleres.add(cuenta.tallerId()); return cuenta;
    }
    @AfterEach void limpiar() {
        // Las peticiones confirman cambios reales. Quitamos solamente los datos creados por esta prueba.
        new TransactionTemplate(transacciones).executeWithoutResult(estado->{
            for(long taller:talleres) {
                for(String tabla:List.of("codigo_qr","foto_orden","tarea_orden","repuesto_orden","orden_trabajo","turno","detalle_presupuesto",
                        "presupuesto","compatibilidad_repuesto","movimiento_stock","repuesto","historial_titularidad","vehiculo","cliente","usuario","mecanico"))
                    jdbc.update("DELETE FROM octano."+tabla+" WHERE taller_id=?",taller);
                jdbc.update("DELETE FROM octano.taller WHERE id=?",taller);
            }
            if(modelo!=0) jdbc.update("DELETE FROM octano.modelo WHERE id=?",modelo);
            if(marca!=0) jdbc.update("DELETE FROM octano.marca WHERE id=?",marca);
        });
    }
    private ResultActions enviar(String ruta,String body) throws Exception {
        return api.perform(post(ruta).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private JsonNode leer(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }
    private long crear(String ruta,String body) throws Exception { return leer(enviar(ruta,body).andExpect(status().isCreated())).get("id").asLong(); }
    private long crearVehiculo(String patente) throws Exception {
        return crear("/api/vehiculos","{\"clienteId\":"+cliente+",\"modeloId\":"+modelo+",\"patente\":\""+patente+"\",\"anio\":2020}");
    }
    private List<String> lote() throws Exception {
        var respuesta=leer(enviar("/api/codigos-qr/lotes","").andExpect(status().isCreated()));
        var codigos=new ArrayList<String>();
        for(var qr:respuesta) codigos.add(qr.get("codigo").asString());
        return codigos;
    }
    private ResultActions asignar(String codigo,long id) throws Exception {
        return api.perform(put("/api/codigos-qr/{codigo}/vehiculo",codigo).header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"vehiculoId\":"+id+"}"));
    }
    private ResultActions consultar(String codigo) throws Exception { return api.perform(get("/api/codigos-qr/{codigo}",codigo).header("Authorization",token)); }
    private ResultActions publico(String codigo) throws Exception { return api.perform(get("/api/publico/qr/{codigo}",codigo)); }
    private String seleccion(List<String> codigos) { return json.writeValueAsString(java.util.Map.of("codigos",codigos)); }

    @Test void generaExactamenteDieciseisCodigosDisponibles() throws Exception {
        // Cada lote tiene 16 etiquetas distintas, sin vehículo y con el usuario que las creó.
        var primero=lote(); var segundo=lote();
        assertEquals(16,primero.size()); assertEquals(32,java.util.stream.Stream.concat(primero.stream(),segundo.stream()).distinct().count());
        assertTrue(primero.stream().allMatch(c->c.matches("OCT-[A-F0-9]{32}")));
        consultar(primero.getFirst()).andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.vehiculoId").isEmpty()).andExpect(jsonPath("$.usuarioModificacionId").value(admin.usuarioId()));
        api.perform(get("/api/codigos-qr").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(32));
    }

    @Test void laImagenSePuedeEscanearYContieneLaRutaDelFrontend() throws Exception {
        // Leemos el PNG con un lector QR y comprobamos el enlace que verá el teléfono.
        String codigo=lote().getFirst();
        var respuesta=leer(consultar(codigo));
        String url=respuesta.get("urlPublica").asString();
        assertTrue(url.endsWith("/qr/"+codigo));
        byte[] png=api.perform(get("/api/codigos-qr/{codigo}/imagen",codigo).header("Authorization",token))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        var imagen=ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(280,imagen.getWidth());
        int[] pixeles=imagen.getRGB(0,0,imagen.getWidth(),imagen.getHeight(),null,0,imagen.getWidth());
        var fuente=new RGBLuminanceSource(imagen.getWidth(),imagen.getHeight(),pixeles);
        assertEquals(url,new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(fuente))).getText());
        api.perform(get("/api/codigos-qr/{codigo}/imagen",codigo).param("tamano","10000").header("Authorization",token)).andExpect(status().isBadRequest());
    }

    @Test void elQrLibreMuestraPendienteSinDatosDeUnVehiculo() throws Exception {
        // No hace falta iniciar sesión para escanear; un QR libre todavía no muestra una ficha.
        String codigo=lote().getFirst();
        publico(codigo).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.vehiculo").isEmpty()).andExpect(jsonPath("$.ultimoService").isEmpty());
        publico("OCT-NO-EXISTE").andExpect(status().isNotFound());
        publico("a").andExpect(status().isBadRequest());
        api.perform(get("/api/vehiculos/"+vehiculo)).andExpect(status().isUnauthorized());
    }

    @Test void vinculaYDevuelveLaFichaSinExponerElCliente() throws Exception {
        // El QR muestra el auto, pero no el nombre, documento, email ni ID del cliente.
        String codigo=lote().getFirst();
        asignar(codigo,vehiculo).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ASIGNADO"));
        api.perform(get("/api/vehiculos/"+vehiculo).header("Authorization",token)).andExpect(jsonPath("$.codigoQr").value(codigo));
        var resultado=publico(codigo).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.vehiculo.patente").value("ABC-123"))
                .andExpect(jsonPath("$.vehiculo.marca").value("Marca QR"))
                .andExpect(jsonPath("$.vehiculo.modelo").value("Modelo QR"))
                .andExpect(jsonPath("$.vehiculo.kilometraje").isEmpty())
                .andExpect(jsonPath("$.ultimoService").isEmpty()).andExpect(jsonPath("$.ultimaDistribucion").isEmpty());
        String cuerpo=resultado.andReturn().getResponse().getContentAsString();
        for(String privado:List.of("Cliente privado","12345678","cliente@privado.test","clienteId","usuarioModificacionId","precio","diagnostico"))
            assertFalse(cuerpo.contains(privado));
    }

    @Test void reemplazarElQrLiberaElAnteriorSinBorrarSuImpresion() throws Exception {
        // Igual que en el front: el QR anterior vuelve a disponible y conserva su marca de impresión.
        var lote=lote();
        enviar("/api/codigos-qr/impresiones",seleccion(lote)).andExpect(status().isOk());
        String viejo=lote.get(0),nuevo=lote.get(1);
        asignar(viejo,vehiculo).andExpect(status().isOk()); asignar(nuevo,vehiculo).andExpect(status().isOk());
        consultar(viejo).andExpect(jsonPath("$.estado").value("DISPONIBLE")).andExpect(jsonPath("$.impresoEn").isNotEmpty())
                .andExpect(jsonPath("$.asignadoEn").isEmpty());
        publico(viejo).andExpect(jsonPath("$.vehiculo").isEmpty());
        publico(nuevo).andExpect(jsonPath("$.vehiculo.patente").value("ABC-123"));
        api.perform(get("/api/vehiculos/"+vehiculo).header("Authorization",token)).andExpect(jsonPath("$.codigoQr").value(nuevo));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM octano.codigo_qr WHERE vehiculo_id=?",Long.class,vehiculo));
    }

    @Test void soloPermiteAsignarCodigosExistentesYDisponibles() throws Exception {
        // Escribir un código desconocido no crea una etiqueta. Tampoco se roba un QR asignado.
        String codigo=lote().getFirst(); long otro=crearVehiculo("xyz987");
        asignar("OCT-INVENTADO",vehiculo).andExpect(status().isNotFound());
        asignar(codigo,vehiculo).andExpect(status().isOk());
        asignar(codigo,vehiculo).andExpect(status().isBadRequest());
        asignar(codigo,otro).andExpect(status().isBadRequest());
        api.perform(delete("/api/codigos-qr/{codigo}/vehiculo",codigo).header("Authorization",token)).andExpect(status().isOk());
        api.perform(get("/api/vehiculos/"+vehiculo).header("Authorization",token)).andExpect(jsonPath("$.codigoQr").isEmpty());
        publico(codigo).andExpect(jsonPath("$.estado").value("DISPONIBLE"));
        asignar(codigo,otro).andExpect(status().isOk());
        publico(codigo).andExpect(jsonPath("$.vehiculo.patente").value("XYZ-987"));
    }

    @Test void laImpresionExigeUnaHojaCompletaSinDuplicados() throws Exception {
        // La hoja tiene 16 códigos distintos; después de marcarla no puede volver a prepararse.
        var lote=lote();
        enviar("/api/codigos-qr/impresiones",seleccion(lote.subList(0,15))).andExpect(status().isBadRequest());
        var repetidos=new ArrayList<>(lote); repetidos.set(15,lote.getFirst());
        enviar("/api/codigos-qr/impresiones",seleccion(repetidos)).andExpect(status().isBadRequest());
        enviar("/api/codigos-qr/plantillas",seleccion(lote)).andExpect(status().isOk());
        consultar(lote.getFirst()).andExpect(jsonPath("$.impresoEn").isEmpty());
        enviar("/api/codigos-qr/impresiones",seleccion(lote)).andExpect(status().isOk()).andExpect(jsonPath("$[0].impresoPor").value(admin.usuarioId()));
        enviar("/api/codigos-qr/plantillas",seleccion(lote)).andExpect(status().isBadRequest());
        enviar("/api/codigos-qr/impresiones",seleccion(lote)).andExpect(status().isBadRequest());
    }

    @Test void unCodigoAjenoImpideImprimirTodaLaSeleccion() throws Exception {
        // Si una etiqueta no pertenece al taller, ninguna de las otras queda marcada por error.
        var propios=lote(); var otro=cuenta("otro@qr.test");
        token=otro.bearer(); String ajeno=lote().getFirst(); token=admin.bearer();
        var mezcla=new ArrayList<>(propios); mezcla.set(15,ajeno);
        enviar("/api/codigos-qr/impresiones",seleccion(mezcla)).andExpect(status().isNotFound());
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM octano.codigo_qr WHERE taller_id=? AND impreso_en IS NOT NULL",Long.class,admin.tallerId()));
        consultar(ajeno).andExpect(status().isNotFound());
        asignar(ajeno,vehiculo).andExpect(status().isNotFound());
    }

    @Test void soloAdminGestionaLosQrYRecepcionPuedeConsultarlos() throws Exception {
        // RECEPCION puede leer; generar, asignar e imprimir requiere ADMIN.
        String codigo=lote().getFirst();
        crear("/api/usuarios","{\"email\":\"recepcion@qr.test\",\"password\":\"ClaveDePrueba123!\",\"rol\":\"RECEPCION\"}");
        var login=leer(api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"recepcion@qr.test\",\"password\":\"ClaveDePrueba123!\"}")).andExpect(status().isOk()));
        token="Bearer "+login.get("tokenAcceso").asString();
        consultar(codigo).andExpect(status().isOk());
        enviar("/api/codigos-qr/lotes","").andExpect(status().isForbidden()); asignar(codigo,vehiculo).andExpect(status().isForbidden());
        api.perform(get("/api/codigos-qr")).andExpect(status().isUnauthorized());
    }

    @Test void laFichaUsaLosCierresRealesYNoPublicaTrabajosPendientes() throws Exception {
        // Solo se ve el último cierre de cada tipo. Kilometraje cero y filtros vacíos son datos válidos.
        String codigo=lote().getFirst(); asignar(codigo,vehiculo).andExpect(status().isOk());
        long mecanico=crear("/api/mecanicos","{\"nombre\":\"Juan\",\"apellido\":\"Perez\",\"telefono\":\"02392 456789\",\"fechaInicio\":\"2020-01-01\"}");
        cerrarOrden(mecanico,"SERVICE",50000,"Aceite anterior","[\"ACEITE\"]");
        cerrarOrden(mecanico,"SERVICE",0,"Aceite registrado","[]");
        long distribucion=orden(mecanico,"DISTRIBUCION");
        enviar("/api/ordenes-trabajo/"+distribucion+"/repuestos","{\"items\":[{\"descripcion\":\"Kit de correa\",\"cantidad\":1,\"precioUnitario\":100}]}").andExpect(status().isOk());
        finalizar(distribucion,100,"","[]");
        orden(mecanico,"SERVICE");
        publico(codigo).andExpect(status().isOk()).andExpect(jsonPath("$.ultimoService.kilometraje").value(0))
                .andExpect(jsonPath("$.ultimoService.aceite").value("Aceite registrado"))
                .andExpect(jsonPath("$.ultimoService.filtros.length()").value(0))
                .andExpect(jsonPath("$.ultimoService.observaciones").value("Observación pública"))
                .andExpect(jsonPath("$.ultimaDistribucion.kilometraje").value(100))
                .andExpect(jsonPath("$.ultimaDistribucion.repuestos[0]").value("Kit de correa"));
    }
    private long orden(long mecanico,String tipo) throws Exception {
        return leer(enviar("/api/ordenes-trabajo","""
                {"vehiculoId":%d,"mecanicoId":%d,"descripcion":"Trabajo privado","indicaciones":"Nota privada",
                "tiposTrabajo":["%s"],"manoObra":50,"porcentajeIva":21}
                """.formatted(vehiculo,mecanico,tipo)).andExpect(status().isCreated())).get("orden").get("id").asLong();
    }
    private void cerrarOrden(long mecanico,String tipo,int km,String aceite,String filtros) throws Exception { finalizar(orden(mecanico,tipo),km,aceite,filtros); }
    private void finalizar(long id,int km,String aceite,String filtros) throws Exception {
        enviar("/api/ordenes-trabajo/"+id+"/inicio","").andExpect(status().isOk());
        enviar("/api/ordenes-trabajo/"+id+"/finalizacion","{\"kilometraje\":"+km+",\"aceite\":\""+aceite+"\",\"filtros\":"+filtros+",\"observacionesMecanico\":\"Observación pública\"}").andExpect(status().isOk());
    }

    @Test void dosVehiculosNoSePuedenQuedarConElMismoQr() throws Exception {
        // Dos asignaciones simultáneas: una gana y la otra debe elegir otra etiqueta.
        String codigo=lote().getFirst(); long otro=crearVehiculo("xyz987");
        var salida=new CountDownLatch(1);
        try(var ejecutor=Executors.newFixedThreadPool(2)) {
            var a=ejecutor.submit(()->{salida.await(5,TimeUnit.SECONDS);return asignar(codigo,vehiculo).andReturn().getResponse().getStatus();});
            var b=ejecutor.submit(()->{salida.await(5,TimeUnit.SECONDS);return asignar(codigo,otro).andReturn().getResponse().getStatus();});
            salida.countDown();
            assertEquals(List.of(200,400),List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM octano.codigo_qr WHERE codigo=? AND vehiculo_id IS NOT NULL",Long.class,codigo));
    }
}
