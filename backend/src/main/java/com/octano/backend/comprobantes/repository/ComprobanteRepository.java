package com.octano.backend.comprobantes.repository;

import com.octano.backend.comprobantes.dto.ComprobantesDatos.Pendiente;
import com.octano.backend.comprobantes.entity.Comprobante;
import com.octano.backend.comprobantes.entity.Comprobante.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ComprobanteRepository {
    private static final String ESTADO="CASE WHEN b.id IS NOT NULL THEN 'COBRADO' ELSE c.estado END";
    private static final String DESDE=" FROM octano.comprobante c LEFT JOIN octano.cobro b ON b.comprobante_id=c.id AND b.taller_id=c.taller_id ";
    private static final String CONSULTA="SELECT c.*,"+ESTADO+" AS estado_actual"+DESDE;
    private static final String FILTRO="""
             WHERE c.taller_id=:taller AND (CAST(:orden AS bigint) IS NULL OR c.orden_id=:orden)
             AND (CAST(:cliente AS bigint) IS NULL OR c.cliente_id=:cliente)
             AND (CAST(:vehiculo AS bigint) IS NULL OR c.vehiculo_id=:vehiculo)
             AND (CAST(:desde AS date) IS NULL OR c.fecha>=:desde) AND (CAST(:hasta AS date) IS NULL OR c.fecha<=:hasta)
             AND strpos(lower(concat_ws(' ',c.id,c.numero,c.descripcion,c.receptor_nombre,c.receptor_documento,c.patente,c.marca,c.modelo)),lower(:q))>0
            """+" AND (CAST(:estado AS varchar) IS NULL OR "+ESTADO+"=:estado) ";
    private static Instant instante(ResultSet rs,String columna) throws SQLException {
        var fecha=rs.getTimestamp(columna); return fecha==null?null:fecha.toInstant();
    }
    private static Parte parte(ResultSet rs,String prefijo) throws SQLException {
        return new Parte(rs.getString(prefijo+"_nombre"),rs.getString(prefijo+"_documento"),rs.getString(prefijo+"_calle"),
                rs.getObject(prefijo+"_altura",Integer.class),rs.getString(prefijo+"_iva"),rs.getString(prefijo+"_telefono"));
    }
    private static final RowMapper<Comprobante> FILA=(rs,n)->new Comprobante(rs.getLong("id"),rs.getLong("taller_id"),rs.getObject("orden_id",Long.class),
            rs.getLong("cliente_id"),rs.getLong("vehiculo_id"),rs.getObject("fecha",LocalDate.class),rs.getString("descripcion"),
            Estado.valueOf(rs.getString("estado_actual")),rs.getObject("numero",Long.class),rs.getBigDecimal("mano_obra"),rs.getBigDecimal("porcentaje_iva"),
            rs.getBigDecimal("total_repuestos"),rs.getBigDecimal("subtotal"),rs.getBigDecimal("monto_iva"),rs.getBigDecimal("total"),
            parte(rs,"emisor"),parte(rs,"receptor"),new Vehiculo(rs.getString("patente"),rs.getString("marca"),rs.getString("modelo"),rs.getString("version"),rs.getInt("anio")),
            instante(rs,"creado_en"),rs.getLong("creado_por"),instante(rs,"emitido_en"),rs.getObject("emitido_por",Long.class),instante(rs,"fecha_modif"),rs.getLong("usuario_modificacion_id"));
    private static final RowMapper<Cobro> FILA_COBRO=(rs,n)->new Cobro(rs.getLong("id"),rs.getLong("comprobante_id"),instante(rs,"fecha"),
            rs.getBigDecimal("importe"),MedioPago.valueOf(rs.getString("medio_pago")),rs.getString("observaciones"),rs.getLong("usuario_id"));
    private final NamedParameterJdbcTemplate jdbc;
    public ComprobanteRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private MapSqlParameterSource clave(Long id,long taller) { return new MapSqlParameterSource("id",id).addValue("taller",taller); }
    public Optional<Comprobante> buscar(long id,long taller,boolean bloquear) {
        return jdbc.query(CONSULTA+" WHERE c.id=:id AND c.taller_id=:taller"+(bloquear?" FOR UPDATE OF c":""),clave(id,taller),FILA).stream().findFirst();
    }
    public Optional<Long> porOrden(long orden,long taller) {
        return jdbc.query("SELECT id FROM octano.comprobante WHERE orden_id=:id AND taller_id=:taller",clave(orden,taller),(rs,n)->rs.getLong(1)).stream().findFirst();
    }
    private MapSqlParameterSource filtros(long taller,String q,Estado estado,Long cliente,Long vehiculo,Long orden,LocalDate desde,LocalDate hasta) {
        return clave(null,taller).addValue("q",q).addValue("estado",estado==null?null:estado.name()).addValue("cliente",cliente)
                .addValue("vehiculo",vehiculo).addValue("orden",orden).addValue("desde",desde).addValue("hasta",hasta);
    }
    public List<Comprobante> listar(long taller,String q,Estado estado,Long cliente,Long vehiculo,Long orden,LocalDate desde,LocalDate hasta,int pagina,int tamano) {
        return jdbc.query(CONSULTA+FILTRO+" ORDER BY c.fecha DESC,c.id DESC LIMIT :limite OFFSET :offset",
                filtros(taller,q,estado,cliente,vehiculo,orden,desde,hasta).addValue("limite",tamano).addValue("offset",(long)pagina*tamano),FILA);
    }
    public long contar(long taller,String q,Estado estado,Long cliente,Long vehiculo,Long orden,LocalDate desde,LocalDate hasta) {
        return jdbc.queryForObject("SELECT count(*)"+DESDE+FILTRO,filtros(taller,q,estado,cliente,vehiculo,orden,desde,hasta),Long.class);
    }
    private MapSqlParameterSource partes(MapSqlParameterSource params,Parte emisor,Parte receptor,Vehiculo vehiculo) {
        for(String prefijo:List.of("emisor","receptor")) {
            Parte p=prefijo.equals("emisor")?emisor:receptor;
            params.addValue(prefijo+"Nombre",p.nombre()).addValue(prefijo+"Documento",p.documento()).addValue(prefijo+"Calle",p.calle())
                    .addValue(prefijo+"Altura",p.altura()).addValue(prefijo+"Iva",p.condicionIva()).addValue(prefijo+"Telefono",p.telefono());
        }
        return params.addValue("patente",vehiculo.patente()).addValue("marca",vehiculo.marca()).addValue("modelo",vehiculo.modelo())
                .addValue("version",vehiculo.version()).addValue("anio",vehiculo.anio());
    }
    private static final String VALORES_PARTES="""
            emisor_nombre=:emisorNombre,emisor_documento=:emisorDocumento,emisor_calle=:emisorCalle,emisor_altura=:emisorAltura,
            emisor_iva=:emisorIva,emisor_telefono=:emisorTelefono,receptor_nombre=:receptorNombre,receptor_documento=:receptorDocumento,
            receptor_calle=:receptorCalle,receptor_altura=:receptorAltura,receptor_iva=:receptorIva,receptor_telefono=:receptorTelefono,
            patente=:patente,marca=:marca,modelo=:modelo,version=:version,anio=:anio
            """;
    public long guardar(Comprobante c,long usuario,List<Item> items) {
        var params=partes(clave(c.id(),c.tallerId()),c.emisor(),c.receptor(),c.vehiculo()).addValue("orden",c.ordenId()).addValue("cliente",c.clienteId())
                .addValue("vehiculo",c.vehiculoId()).addValue("fecha",c.fecha()).addValue("descripcion",c.descripcion()).addValue("usuario",usuario)
                .addValue("manoObra",c.manoObra()).addValue("porcentaje",c.porcentajeIva()).addValue("repuestos",c.totalRepuestos())
                .addValue("subtotal",c.subtotal()).addValue("iva",c.montoIva()).addValue("total",c.total());
        long id;
        if(c.id()==null) {
            id=jdbc.queryForObject("""
                    INSERT INTO octano.comprobante(taller_id,orden_id,cliente_id,vehiculo_id,fecha,descripcion,mano_obra,porcentaje_iva,total_repuestos,
                    subtotal,monto_iva,total,emisor_nombre,emisor_documento,emisor_calle,emisor_altura,emisor_iva,emisor_telefono,
                    receptor_nombre,receptor_documento,receptor_calle,receptor_altura,receptor_iva,receptor_telefono,
                    patente,marca,modelo,version,anio,creado_por,usuario_modificacion_id)
                    VALUES (:taller,:orden,:cliente,:vehiculo,:fecha,:descripcion,:manoObra,:porcentaje,:repuestos,:subtotal,:iva,:total,
                    :emisorNombre,:emisorDocumento,:emisorCalle,:emisorAltura,:emisorIva,:emisorTelefono,
                    :receptorNombre,:receptorDocumento,:receptorCalle,:receptorAltura,:receptorIva,:receptorTelefono,
                    :patente,:marca,:modelo,:version,:anio,:usuario,:usuario) RETURNING id
                    """,params,Long.class);
        } else {
            id=c.id();
            jdbc.update("UPDATE octano.comprobante SET cliente_id=:cliente,vehiculo_id=:vehiculo,descripcion=:descripcion,mano_obra=:manoObra,"
                    +"porcentaje_iva=:porcentaje,total_repuestos=:repuestos,subtotal=:subtotal,monto_iva=:iva,total=:total,"
                    +VALORES_PARTES+",fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario WHERE id=:id AND taller_id=:taller",params);
            jdbc.update("DELETE FROM octano.detalle_comprobante WHERE comprobante_id=:id AND taller_id=:taller",clave(id,c.tallerId()));
        }
        for(var item:items) {
            jdbc.update("""
                    INSERT INTO octano.detalle_comprobante(taller_id,comprobante_id,renglon,repuesto_id,codigo_oem,descripcion,cantidad,precio_unitario,subtotal)
                    VALUES (:taller,:id,:renglon,:repuesto,:codigo,:descripcion,:cantidad,:precio,:subtotal)
                    """,clave(id,c.tallerId()).addValue("renglon",item.renglon()).addValue("repuesto",item.repuestoId()).addValue("codigo",item.codigoOem())
                    .addValue("descripcion",item.descripcion()).addValue("cantidad",item.cantidad()).addValue("precio",item.precioUnitario()).addValue("subtotal",item.subtotal()));
        }
        return id;
    }
    public List<Item> items(long id,long taller) {
        return jdbc.query("SELECT * FROM octano.detalle_comprobante WHERE comprobante_id=:id AND taller_id=:taller ORDER BY renglon",clave(id,taller),
                (rs,n)->new Item(rs.getInt("renglon"),rs.getObject("repuesto_id",Long.class),rs.getString("codigo_oem"),rs.getString("descripcion"),
                        rs.getBigDecimal("cantidad"),rs.getBigDecimal("precio_unitario"),rs.getBigDecimal("subtotal")));
    }
    public void emitir(long id,long taller,long usuario,Parte emisor,Parte receptor,Vehiculo vehiculo) {
        // El servicio bloquea primero el taller: dos emisiones no reciben el mismo número interno.
        Long numero=jdbc.queryForObject("SELECT coalesce(max(numero),0)+1 FROM octano.comprobante WHERE taller_id=:taller",clave(id,taller),Long.class);
        var params=partes(clave(id,taller),emisor,receptor,vehiculo).addValue("usuario",usuario).addValue("numero",numero);
        jdbc.update("UPDATE octano.comprobante SET estado='EMITIDO',numero=:numero,emitido_en=CURRENT_TIMESTAMP,emitido_por=:usuario,"
                +VALORES_PARTES+",fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario WHERE id=:id AND taller_id=:taller",params);
    }
    public Optional<Cobro> cobro(long id,long taller) {
        return jdbc.query("SELECT * FROM octano.cobro WHERE comprobante_id=:id AND taller_id=:taller",clave(id,taller),FILA_COBRO).stream().findFirst();
    }
    public void cobrar(long id,long taller,long usuario,MedioPago medio,String observaciones) {
        // El importe se toma del comprobante emitido, nunca del cuerpo enviado por el navegador.
        jdbc.update("""
                INSERT INTO octano.cobro(taller_id,comprobante_id,importe,medio_pago,observaciones,usuario_id)
                SELECT taller_id,id,total,:medio,:observaciones,:usuario FROM octano.comprobante WHERE id=:id AND taller_id=:taller AND estado='EMITIDO'
                """,clave(id,taller).addValue("usuario",usuario).addValue("medio",medio.name()).addValue("observaciones",observaciones));
        jdbc.update("UPDATE octano.comprobante SET fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario WHERE id=:id AND taller_id=:taller",
                clave(id,taller).addValue("usuario",usuario));
    }
    private static final String FILTRO_COBRO="""
             WHERE taller_id=:taller AND (CAST(:desde AS date) IS NULL OR (fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date>=:desde)
             AND (CAST(:hasta AS date) IS NULL OR (fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date<=:hasta)
             AND (CAST(:medio AS varchar) IS NULL OR medio_pago=:medio)
            """;
    public List<Cobro> cobros(long taller,LocalDate desde,LocalDate hasta,MedioPago medio,int pagina,int tamano) {
        return jdbc.query("SELECT * FROM octano.cobro"+FILTRO_COBRO+" ORDER BY fecha DESC,id DESC LIMIT :limite OFFSET :offset",
                filtrosCobro(taller,desde,hasta,medio).addValue("limite",tamano).addValue("offset",(long)pagina*tamano),FILA_COBRO);
    }
    public long contarCobros(long taller,LocalDate desde,LocalDate hasta,MedioPago medio) {
        return jdbc.queryForObject("SELECT count(*) FROM octano.cobro"+FILTRO_COBRO,filtrosCobro(taller,desde,hasta,medio),Long.class);
    }
    private MapSqlParameterSource filtrosCobro(long taller,LocalDate desde,LocalDate hasta,MedioPago medio) {
        return clave(null,taller).addValue("desde",desde).addValue("hasta",hasta).addValue("medio",medio==null?null:medio.name());
    }
    private static final String PENDIENTES="""
             FROM octano.orden_trabajo o JOIN octano.cliente cl ON cl.id=o.cliente_id AND cl.taller_id=o.taller_id
             JOIN octano.vehiculo v ON v.id=o.vehiculo_id AND v.taller_id=o.taller_id
             JOIN octano.modelo mo ON mo.id=v.modelo_id JOIN octano.marca ma ON ma.id=mo.marca_id
             WHERE o.taller_id=:taller AND o.estado='FINALIZADO'
             AND NOT EXISTS(SELECT 1 FROM octano.comprobante c WHERE c.orden_id=o.id AND c.taller_id=o.taller_id)
             AND (CAST(:desde AS date) IS NULL OR (o.fin_fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date>=:desde)
             AND (CAST(:hasta AS date) IS NULL OR (o.fin_fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date<=:hasta)
             AND (CAST(:presupuesto AS boolean) IS NULL OR (o.presupuesto_id IS NOT NULL)=:presupuesto)
             AND strpos(lower(concat_ws(' ',o.id,o.descripcion,cl.nombre,v.patente,ma.nombre,mo.nombre)),lower(:q))>0
            """;
    private MapSqlParameterSource filtrosPendientes(long taller,String q,LocalDate desde,LocalDate hasta,Boolean presupuesto) {
        return clave(null,taller).addValue("q",q).addValue("desde",desde).addValue("hasta",hasta).addValue("presupuesto",presupuesto);
    }
    public long contarPendientes(long taller,String q,LocalDate desde,LocalDate hasta,Boolean presupuesto) {
        return jdbc.queryForObject("SELECT count(*)"+PENDIENTES,filtrosPendientes(taller,q,desde,hasta,presupuesto),Long.class);
    }
    public List<Pendiente> pendientes(long taller,String q,LocalDate desde,LocalDate hasta,Boolean presupuesto,int pagina,int tamano) {
        return jdbc.query("""
                SELECT o.id,o.cliente_id,o.vehiculo_id,cl.nombre AS cliente_nombre,v.patente,ma.nombre AS marca,mo.nombre AS modelo,
                o.descripcion,o.presupuesto_id,o.fin_fecha,o.mano_obra,o.porcentaje_iva,
                coalesce((SELECT sum(r.subtotal) FROM octano.repuesto_orden r WHERE r.orden_id=o.id AND r.taller_id=o.taller_id),0) AS materiales
                """+PENDIENTES+" ORDER BY o.fin_fecha DESC,o.id DESC LIMIT :limite OFFSET :offset",
                filtrosPendientes(taller,q,desde,hasta,presupuesto).addValue("limite",tamano).addValue("offset",(long)pagina*tamano),(rs,n)->{
                    var subtotal=rs.getBigDecimal("materiales").add(rs.getBigDecimal("mano_obra"));
                    var iva=subtotal.multiply(rs.getBigDecimal("porcentaje_iva").movePointLeft(2)).setScale(2,java.math.RoundingMode.HALF_UP);
                    return new Pendiente(rs.getLong("id"),rs.getLong("cliente_id"),rs.getLong("vehiculo_id"),rs.getString("cliente_nombre"),
                            rs.getString("patente"),rs.getString("marca"),rs.getString("modelo"),rs.getString("descripcion"),rs.getObject("presupuesto_id",Long.class),
                            instante(rs,"fin_fecha"),subtotal.add(iva));
                });
    }
}
