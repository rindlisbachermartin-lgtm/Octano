package com.octano.backend.ordenes.repository;

import com.octano.backend.ordenes.dto.OrdenesDatos.*;
import com.octano.backend.ordenes.entity.OrdenTrabajo;
import com.octano.backend.ordenes.entity.OrdenTrabajo.Estado;
import com.octano.backend.presupuestos.entity.Presupuesto.TipoTrabajo;
import com.octano.backend.turnos.entity.Turno;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrdenTrabajoRepository {
    private static final String DESDE="""
         FROM octano.orden_trabajo o JOIN octano.cliente c ON c.id=o.cliente_id AND c.taller_id=o.taller_id
         JOIN octano.vehiculo v ON v.id=o.vehiculo_id AND v.taller_id=o.taller_id
         JOIN octano.modelo mo ON mo.id=v.modelo_id JOIN octano.marca ma ON ma.id=mo.marca_id
         LEFT JOIN octano.version_vehiculo ve ON ve.id=v.version_id
         LEFT JOIN octano.mecanico m ON m.id=o.mecanico_id AND m.taller_id=o.taller_id
         LEFT JOIN octano.turno t ON t.id=o.turno_id AND t.taller_id=o.taller_id
        """;
    private static final String CONSULTA="SELECT o.*, c.nombre AS cliente_nombre,v.patente,ma.nombre AS marca,mo.nombre AS modelo,ve.nombre AS version_nombre,concat_ws(' ',m.nombre,m.apellido) AS mecanico_nombre,t.fecha AS fecha_turno,t.hora_inicio AS hora_inicio_turno,t.hora_fin AS hora_fin_turno"+DESDE;
    private static final String BUSQUEDA="""
         CROSS JOIN LATERAL (SELECT array_agg(regexp_replace(normalize(lower(valor), NFD), U&'[\\0300-\\036f]', '', 'g')) AS valores
             FROM unnest(ARRAY[o.id::text,o.descripcion,c.nombre,v.patente,ma.nombre,mo.nombre,concat_ws(' ',m.nombre,m.apellido)]) AS campos(valor)) n
         CROSS JOIN LATERAL (SELECT array_to_string(n.valores, ' ') AS texto,
             (SELECT string_agg(regexp_replace(valor, '[^a-z0-9]', '', 'g'), ' ') FROM unnest(n.valores) AS campos(valor)) AS limpio) b
        """;
    private static final String FILTRO="""
         WHERE o.taller_id=:taller AND (CAST(:mecanico AS bigint) IS NULL OR o.mecanico_id=:mecanico)
         AND (CAST(:vehiculo AS bigint) IS NULL OR o.vehiculo_id=:vehiculo)
         AND (CAST(:cliente AS bigint) IS NULL OR o.cliente_id=:cliente)
         AND (CAST(:estado AS varchar) IS NULL OR o.estado=:estado)
         AND (CAST(:desde AS date) IS NULL OR (o.fecha_creacion AT TIME ZONE 'America/Argentina/Buenos_Aires')::date >= :desde)
         AND (CAST(:hasta AS date) IS NULL OR (o.fecha_creacion AT TIME ZONE 'America/Argentina/Buenos_Aires')::date <= :hasta)
         AND (strpos(b.texto,:q)>0 OR (length(:limpio)>=2 AND strpos(b.limpio,:limpio)>0)
             OR (cardinality(regexp_split_to_array(:q,'\\s+'))>1 AND NOT EXISTS (
                 SELECT 1 FROM unnest(regexp_split_to_array(:q,'\\s+')) AS palabras(palabra)
                 WHERE NOT (strpos(b.texto,palabra)>0 OR (length(regexp_replace(palabra,'[^a-z0-9]','','g'))>=2
                     AND strpos(b.limpio,regexp_replace(palabra,'[^a-z0-9]','','g'))>0)))))
        """;
    private static Instant instante(ResultSet rs,String columna) throws SQLException {
        var t=rs.getTimestamp(columna); return t==null?null:t.toInstant();
    }
    private static List<String> textos(ResultSet rs,String columna) throws SQLException {
        return Arrays.asList((String[])rs.getArray(columna).getArray());
    }
    private static final RowMapper<OrdenTrabajo> FILA=(rs,n)->new OrdenTrabajo(rs.getLong("id"),rs.getLong("taller_id"),rs.getLong("cliente_id"),
            rs.getLong("vehiculo_id"),rs.getObject("turno_id",Long.class),rs.getObject("presupuesto_id",Long.class),rs.getObject("mecanico_id",Long.class),
            rs.getString("descripcion"),textos(rs,"tipos_trabajo").stream().map(TipoTrabajo::valueOf).toList(),rs.getString("indicaciones"),
            rs.getString("diagnostico"),rs.getString("observaciones_mecanico"),rs.getObject("kilometraje",Integer.class),rs.getString("aceite"),textos(rs,"filtros"),
            rs.getBigDecimal("mano_obra"),rs.getBigDecimal("porcentaje_iva"),Estado.valueOf(rs.getString("estado")),rs.getString("motivo_cancelacion"),
            instante(rs,"fecha_creacion"),instante(rs,"ingreso_fecha"),instante(rs,"inicio_fecha"),instante(rs,"fin_fecha"),instante(rs,"estado_fecha"),
            rs.getLong("estado_usuario_id"),instante(rs,"fecha_modif"),rs.getLong("usuario_modificacion_id"),rs.getString("cliente_nombre"),
            rs.getString("patente"),rs.getString("marca"),rs.getString("modelo"),rs.getString("version_nombre"),rs.getString("mecanico_nombre"),
            rs.getObject("fecha_turno",LocalDate.class),rs.getObject("hora_inicio_turno",LocalTime.class),rs.getObject("hora_fin_turno",LocalTime.class));
    private final NamedParameterJdbcTemplate jdbc;
    public OrdenTrabajoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private MapSqlParameterSource clave(long id,long taller) { return new MapSqlParameterSource("id",id).addValue("taller",taller); }
    public Optional<OrdenTrabajo> buscar(long id,long taller,boolean bloquear) {
        return jdbc.query(CONSULTA+" WHERE o.id=:id AND o.taller_id=:taller"+(bloquear?" FOR UPDATE OF o":""),clave(id,taller),FILA).stream().findFirst();
    }
    public Optional<Long> porTurno(long turno,long taller) {
        return jdbc.query("SELECT id FROM octano.orden_trabajo WHERE turno_id=:id AND taller_id=:taller",clave(turno,taller),(rs,n)->rs.getLong(1)).stream().findFirst();
    }
    public boolean presupuestoUtilizado(long presupuesto,long taller,Long excluir) {
        return jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM octano.orden_trabajo WHERE presupuesto_id=:id AND taller_id=:taller
                AND estado<>'CANCELADO' AND (CAST(:excluir AS bigint) IS NULL OR id<>:excluir))
                """,clave(presupuesto,taller).addValue("excluir",excluir),Boolean.class);
    }
    public boolean presupuestoIngresado(long presupuesto,long taller) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM octano.orden_trabajo WHERE presupuesto_id=:id AND taller_id=:taller AND estado IN ('EN_ESPERA','EN_PROCESO','FINALIZADO'))",
                clave(presupuesto,taller),Boolean.class);
    }
    public void cancelarPorPresupuesto(long presupuesto,long taller,long usuario) {
        jdbc.update("""
                UPDATE octano.orden_trabajo SET estado='CANCELADO',motivo_cancelacion='Presupuesto rechazado o anulado.',
                estado_fecha=CURRENT_TIMESTAMP,estado_usuario_id=:usuario,fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario
                WHERE presupuesto_id=:id AND taller_id=:taller AND estado='PENDIENTE_INGRESO'
                """,clave(presupuesto,taller).addValue("usuario",usuario));
    }
    private MapSqlParameterSource filtros(long taller,String q,Estado estado,Long cliente,Long vehiculo,Long mecanico,LocalDate desde,LocalDate hasta) {
        return clave(0,taller).addValue("q",q).addValue("limpio",q.replaceAll("[^a-z0-9]", "")).addValue("estado",estado==null?null:estado.name()).addValue("cliente",cliente)
                .addValue("vehiculo",vehiculo).addValue("mecanico",mecanico).addValue("desde",desde).addValue("hasta",hasta);
    }
    public List<OrdenTrabajo> listar(long taller,String q,Estado estado,Long cliente,Long vehiculo,Long mecanico,LocalDate desde,LocalDate hasta,int pagina,int tamano) {
        return jdbc.query(CONSULTA+BUSQUEDA+FILTRO+" ORDER BY o.id DESC LIMIT :limite OFFSET :offset",
                filtros(taller,q,estado,cliente,vehiculo,mecanico,desde,hasta).addValue("limite",tamano).addValue("offset",(long)pagina*tamano),FILA);
    }
    public long contar(long taller,String q,Estado estado,Long cliente,Long vehiculo,Long mecanico,LocalDate desde,LocalDate hasta) {
        return jdbc.queryForObject("SELECT count(*)"+DESDE+BUSQUEDA+FILTRO,filtros(taller,q,estado,cliente,vehiculo,mecanico,desde,hasta),Long.class);
    }
    public long guardar(Long id,long taller,long usuario,long cliente,Solicitud s,BigDecimal manoObra,BigDecimal iva) {
        var p=clave(id==null?0:id,taller).addValue("usuario",usuario).addValue("cliente",cliente).addValue("vehiculo",s.vehiculoId())
                .addValue("presupuesto",s.presupuestoId()).addValue("mecanico",s.mecanicoId()).addValue("descripcion",s.descripcion())
                .addValue("tipos",String.join(",",s.tiposTrabajo().stream().distinct().map(Enum::name).toList()))
                .addValue("indicaciones",s.indicaciones()).addValue("manoObra",manoObra).addValue("iva",iva);
        if(id==null) return jdbc.queryForObject("""
                INSERT INTO octano.orden_trabajo(taller_id,cliente_id,vehiculo_id,presupuesto_id,mecanico_id,descripcion,tipos_trabajo,
                indicaciones,mano_obra,porcentaje_iva,estado,ingreso_fecha,estado_usuario_id,usuario_modificacion_id)
                VALUES (:taller,:cliente,:vehiculo,:presupuesto,:mecanico,:descripcion,string_to_array(:tipos,','),
                :indicaciones,:manoObra,:iva,'EN_ESPERA',CURRENT_TIMESTAMP,:usuario,:usuario) RETURNING id
                """,p,Long.class);
        jdbc.update("""
                UPDATE octano.orden_trabajo SET mecanico_id=:mecanico,descripcion=:descripcion,tipos_trabajo=string_to_array(:tipos,','),
                indicaciones=:indicaciones,mano_obra=:manoObra,porcentaje_iva=:iva,fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario
                WHERE id=:id AND taller_id=:taller
                """,p);
        return id;
    }
    public long crearDesdeTurno(Turno t,long usuario) {
        return jdbc.queryForObject("""
                INSERT INTO octano.orden_trabajo(taller_id,cliente_id,vehiculo_id,turno_id,presupuesto_id,descripcion,tipos_trabajo,
                mano_obra,porcentaje_iva,estado,ingreso_fecha,estado_usuario_id,usuario_modificacion_id)
                SELECT :taller,:cliente,:vehiculo,:id,:presupuesto,:descripcion,COALESCE(p.tipos_trabajo,ARRAY['OTRO']),
                COALESCE(p.mano_obra,0),COALESCE(p.porcentaje_iva,21),:estado,
                CASE WHEN :estado='EN_ESPERA' THEN CURRENT_TIMESTAMP ELSE NULL END,:usuario,:usuario
                FROM (SELECT 1) x LEFT JOIN octano.presupuesto p ON p.id=:presupuesto AND p.taller_id=:taller RETURNING id
                """,clave(t.id(),t.tallerId()).addValue("cliente",t.clienteId()).addValue("vehiculo",t.vehiculoId())
                .addValue("presupuesto",t.presupuestoId()).addValue("descripcion",t.motivoVisita()).addValue("usuario",usuario)
                .addValue("estado",t.estado()==Turno.Estado.EN_TALLER?"EN_ESPERA":"PENDIENTE_INGRESO"),Long.class);
    }
    public void sincronizar(long id,Turno t,long usuario) {
        jdbc.update("""
                UPDATE octano.tarea_orden SET descripcion=:descripcion WHERE orden_id=:id AND taller_id=:taller
                AND descripcion=(SELECT left(descripcion,500) FROM octano.orden_trabajo WHERE id=:id AND taller_id=:taller)
                """,clave(id,t.tallerId()).addValue("descripcion",t.motivoVisita()));
        jdbc.update("""
                UPDATE octano.orden_trabajo SET cliente_id=:cliente,vehiculo_id=:vehiculo,presupuesto_id=:presupuesto,descripcion=:descripcion,
                tipos_trabajo=CASE WHEN presupuesto_id IS DISTINCT FROM :presupuesto THEN COALESCE((SELECT tipos_trabajo FROM octano.presupuesto WHERE id=:presupuesto AND taller_id=:taller),ARRAY['OTRO']) ELSE tipos_trabajo END,
                mano_obra=CASE WHEN presupuesto_id IS DISTINCT FROM :presupuesto THEN COALESCE((SELECT mano_obra FROM octano.presupuesto WHERE id=:presupuesto AND taller_id=:taller),0) ELSE mano_obra END,
                porcentaje_iva=CASE WHEN presupuesto_id IS DISTINCT FROM :presupuesto THEN COALESCE((SELECT porcentaje_iva FROM octano.presupuesto WHERE id=:presupuesto AND taller_id=:taller),21) ELSE porcentaje_iva END,
                fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario WHERE id=:id AND taller_id=:taller
                """,clave(id,t.tallerId()).addValue("cliente",t.clienteId()).addValue("vehiculo",t.vehiculoId())
                .addValue("presupuesto",t.presupuestoId()).addValue("descripcion",t.motivoVisita()).addValue("usuario",usuario));
    }
    public void copiarPresupuesto(long id,long taller,Long presupuesto) {
        jdbc.update("DELETE FROM octano.repuesto_orden WHERE orden_id=:id AND taller_id=:taller AND presupuestado",clave(id,taller));
        if(presupuesto!=null) jdbc.update("""
                INSERT INTO octano.repuesto_orden(taller_id,orden_id,repuesto_id,codigo_oem,descripcion,cantidad,precio_unitario,subtotal,presupuestado)
                SELECT :taller,:id,repuesto_id,codigo_oem,descripcion,cantidad,precio_unitario,subtotal,TRUE
                FROM octano.detalle_presupuesto WHERE presupuesto_id=:presupuesto AND taller_id=:taller ORDER BY renglon
                """,clave(id,taller).addValue("presupuesto",presupuesto));
    }
    public void tocar(long id,long taller,long usuario) {
        jdbc.update("UPDATE octano.orden_trabajo SET fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario WHERE id=:id AND taller_id=:taller",
                clave(id,taller).addValue("usuario",usuario));
    }
    public void estado(long id,long taller,long usuario,Estado estado,String motivo) {
        jdbc.update("""
                UPDATE octano.orden_trabajo SET estado=:estado,motivo_cancelacion=:motivo,estado_fecha=CURRENT_TIMESTAMP,estado_usuario_id=:usuario,
                ingreso_fecha=CASE WHEN :estado='EN_ESPERA' THEN CURRENT_TIMESTAMP ELSE ingreso_fecha END,
                inicio_fecha=CASE WHEN :estado='EN_PROCESO' THEN CURRENT_TIMESTAMP ELSE inicio_fecha END,
                fin_fecha=CASE WHEN :estado='FINALIZADO' THEN CURRENT_TIMESTAMP ELSE fin_fecha END,
                fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario WHERE id=:id AND taller_id=:taller
                """,clave(id,taller).addValue("usuario",usuario).addValue("estado",estado.name()).addValue("motivo",motivo));
    }
    public void trabajo(long id,long taller,Trabajo s) {
        jdbc.update("UPDATE octano.orden_trabajo SET diagnostico=:diagnostico,kilometraje=:km,observaciones_mecanico=:notas WHERE id=:id AND taller_id=:taller",
                clave(id,taller).addValue("diagnostico",s.diagnostico()).addValue("km",s.kilometraje()).addValue("notas",s.observacionesMecanico()));
    }
    public void cierre(long id,long taller,Cierre s) {
        jdbc.update("UPDATE octano.orden_trabajo SET kilometraje=:km,observaciones_mecanico=:notas,aceite=:aceite,filtros=string_to_array(:filtros,',') WHERE id=:id AND taller_id=:taller",
                clave(id,taller).addValue("km",s.kilometraje()).addValue("notas",s.observacionesMecanico()).addValue("aceite",s.aceite())
                .addValue("filtros",String.join(",",s.filtros().stream().distinct().map(Enum::name).toList())));
    }
    public List<Tarea> tareas(long id,long taller) {
        return jdbc.query("SELECT id,descripcion,completada FROM octano.tarea_orden WHERE orden_id=:id AND taller_id=:taller ORDER BY id",
                clave(id,taller),(rs,n)->new Tarea(rs.getLong(1),rs.getString(2),rs.getBoolean(3)));
    }
    public void tarea(long id,long taller,String descripcion) {
        jdbc.update("INSERT INTO octano.tarea_orden(taller_id,orden_id,descripcion) VALUES (:taller,:id,:descripcion)",clave(id,taller).addValue("descripcion",descripcion));
    }
    public boolean tareaEstado(long id,long taller,long tarea,boolean completada) {
        return jdbc.update("UPDATE octano.tarea_orden SET completada=:completada WHERE id=:tarea AND orden_id=:id AND taller_id=:taller",
                clave(id,taller).addValue("tarea",tarea).addValue("completada",completada))>0;
    }
    public boolean quitarTarea(long id,long taller,long tarea) {
        return jdbc.update("DELETE FROM octano.tarea_orden WHERE id=:tarea AND orden_id=:id AND taller_id=:taller",clave(id,taller).addValue("tarea",tarea))>0;
    }
    public List<Item> items(long id,long taller) {
        return jdbc.query("SELECT * FROM octano.repuesto_orden WHERE orden_id=:id AND taller_id=:taller ORDER BY id",clave(id,taller),
                (rs,n)->new Item(rs.getLong("id"),rs.getObject("repuesto_id",Long.class),rs.getString("codigo_oem"),rs.getString("descripcion"),
                rs.getBigDecimal("cantidad"),rs.getBigDecimal("precio_unitario"),rs.getBigDecimal("subtotal"),rs.getBoolean("presupuestado"),rs.getBoolean("consumido")));
    }
    public void item(long id,long taller,ItemSolicitud s,String codigo,String descripcion,BigDecimal precio,BigDecimal subtotal) {
        jdbc.update("""
                INSERT INTO octano.repuesto_orden(taller_id,orden_id,repuesto_id,codigo_oem,descripcion,cantidad,precio_unitario,subtotal)
                VALUES (:taller,:id,:repuesto,:codigo,:descripcion,:cantidad,:precio,:subtotal)
                """,clave(id,taller).addValue("repuesto",s.repuestoId()).addValue("codigo",codigo).addValue("descripcion",descripcion)
                .addValue("cantidad",s.cantidad()).addValue("precio",precio).addValue("subtotal",subtotal));
    }
    public void consumido(long id,long taller,long item) {
        jdbc.update("UPDATE octano.repuesto_orden SET consumido=TRUE WHERE id=:item AND orden_id=:id AND taller_id=:taller",clave(id,taller).addValue("item",item));
    }
    public void quitarItem(long id,long taller,long item) {
        jdbc.update("DELETE FROM octano.repuesto_orden WHERE id=:item AND orden_id=:id AND taller_id=:taller",clave(id,taller).addValue("item",item));
    }
    public void cantidad(long id,long taller,long item,BigDecimal cantidad,BigDecimal subtotal) {
        jdbc.update("UPDATE octano.repuesto_orden SET cantidad=:cantidad,subtotal=:subtotal WHERE id=:item AND orden_id=:id AND taller_id=:taller",
                clave(id,taller).addValue("item",item).addValue("cantidad",cantidad).addValue("subtotal",subtotal));
    }
    public List<Foto> fotos(long id,long taller) {
        return jdbc.query("SELECT id,sector,tipo_contenido,fecha,usuario_id FROM octano.foto_orden WHERE orden_id=:id AND taller_id=:taller ORDER BY id",
                clave(id,taller),(rs,n)->new Foto(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getLong(5)));
    }
    public void foto(long id,long taller,long usuario,String sector,String tipo,byte[] datos) {
        jdbc.update("INSERT INTO octano.foto_orden(taller_id,orden_id,usuario_id,sector,tipo_contenido,datos) VALUES (:taller,:id,:usuario,:sector,:tipo,:datos)",
                clave(id,taller).addValue("usuario",usuario).addValue("sector",sector).addValue("tipo",tipo).addValue("datos",datos));
    }
    public Optional<FotoContenido> foto(long id,long taller,long foto) {
        return jdbc.query("SELECT tipo_contenido,datos FROM octano.foto_orden WHERE id=:foto AND orden_id=:id AND taller_id=:taller",
                clave(id,taller).addValue("foto",foto),(rs,n)->new FotoContenido(rs.getString(1),rs.getBytes(2))).stream().findFirst();
    }
    public boolean quitarFoto(long id,long taller,long foto) {
        return jdbc.update("DELETE FROM octano.foto_orden WHERE id=:foto AND orden_id=:id AND taller_id=:taller",clave(id,taller).addValue("foto",foto))>0;
    }
}
