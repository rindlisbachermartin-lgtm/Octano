package com.octano.backend.codigosqr.repository;

import com.octano.backend.codigosqr.dto.QrDatos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QrRepository {
    public record Registro(String codigo, long tallerId, Long vehiculoId, Instant creadoEn, Instant asignadoEn, Instant inutilizadoEn,
                            Instant impresoEn, Long impresoPor, Instant fechaModif, long usuarioModificacionId,
                            String patente, String marca, String modelo) { }
    private static final String DESDE = """
             FROM octano.codigo_qr q
             LEFT JOIN octano.vehiculo v ON v.id=q.vehiculo_id AND v.taller_id=q.taller_id
             LEFT JOIN octano.modelo mo ON mo.id=v.modelo_id LEFT JOIN octano.marca ma ON ma.id=mo.marca_id
            """;
    private static final String CONSULTA = "SELECT q.*,v.patente,ma.nombre AS marca,mo.nombre AS modelo" + DESDE;
    private static final String FILTRO = """
             WHERE q.taller_id=:taller
             AND (CAST(:estado AS varchar) IS NULL OR (CASE WHEN q.inutilizado_en IS NOT NULL THEN 'INUTILIZADO' WHEN q.vehiculo_id IS NULL THEN 'DISPONIBLE' ELSE 'ASIGNADO' END)=:estado)
             AND (CAST(:impreso AS boolean) IS NULL OR (q.impreso_en IS NOT NULL)=:impreso)
             AND (CAST(:vehiculo AS bigint) IS NULL OR q.vehiculo_id=:vehiculo)
             AND strpos(lower(concat_ws(' ',q.codigo,v.patente,ma.nombre,mo.nombre)),lower(:q))>0
            """;
    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        var fecha = rs.getTimestamp(columna); return fecha == null ? null : fecha.toInstant();
    }
    private static final RowMapper<Registro> FILA = (rs,n) -> new Registro(rs.getString("codigo"),rs.getLong("taller_id"),
            rs.getObject("vehiculo_id",Long.class),instante(rs,"creado_en"),instante(rs,"asignado_en"),instante(rs,"inutilizado_en"),instante(rs,"impreso_en"),
            rs.getObject("impreso_por",Long.class),instante(rs,"fecha_modif"),rs.getLong("usuario_modificacion_id"),
            rs.getString("patente"),rs.getString("marca"),rs.getString("modelo"));
    private final NamedParameterJdbcTemplate jdbc;
    public QrRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private MapSqlParameterSource clave(String codigo,long taller) {
        return new MapSqlParameterSource("codigo",codigo).addValue("taller",taller);
    }
    public Optional<Registro> buscar(String codigo, long taller, boolean bloquear) {
        return jdbc.query(CONSULTA+" WHERE q.codigo=:codigo AND q.taller_id=:taller"+(bloquear?" FOR UPDATE OF q":""),clave(codigo,taller),FILA).stream().findFirst();
    }
    public Optional<Registro> publico(String codigo) {
        return jdbc.query(CONSULTA+" WHERE q.codigo=:codigo",new MapSqlParameterSource("codigo",codigo),FILA).stream().findFirst();
    }
    public void crear(String codigo,long taller,long usuario) {
        jdbc.update("INSERT INTO octano.codigo_qr(codigo,taller_id,usuario_modificacion_id) VALUES (:codigo,:taller,:usuario)",clave(codigo,taller).addValue("usuario",usuario));
    }
    private MapSqlParameterSource filtros(long taller,String q,Estado estado,Boolean impreso,Long vehiculo) {
        return clave("",taller).addValue("q",q).addValue("estado",estado==null?null:estado.name()).addValue("impreso",impreso).addValue("vehiculo",vehiculo);
    }
    public List<Registro> listar(long taller,String q,Estado estado,Boolean impreso,Long vehiculo,int pagina,int tamano) {
        return jdbc.query(CONSULTA+FILTRO+" ORDER BY q.creado_en DESC,q.codigo LIMIT :limite OFFSET :offset",
                filtros(taller,q,estado,impreso,vehiculo).addValue("limite",tamano).addValue("offset",(long)pagina*tamano),FILA);
    }
    public long contar(long taller,String q,Estado estado,Boolean impreso,Long vehiculo) {
        return jdbc.queryForObject("SELECT count(*)"+DESDE+FILTRO,filtros(taller,q,estado,impreso,vehiculo),Long.class);
    }
    public void liberarVehiculo(long vehiculo,long taller,long usuario) {
        jdbc.update("""
                UPDATE octano.codigo_qr SET vehiculo_id=NULL,asignado_en=NULL,inutilizado_en=CURRENT_TIMESTAMP,fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario
                WHERE vehiculo_id=:vehiculo AND taller_id=:taller
                """,clave("",taller).addValue("vehiculo",vehiculo).addValue("usuario",usuario));
    }
    public void asignar(String codigo,long vehiculo,long taller,long usuario) {
        jdbc.update("""
                UPDATE octano.codigo_qr SET vehiculo_id=:vehiculo,asignado_en=CURRENT_TIMESTAMP,fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario
                WHERE codigo=:codigo AND taller_id=:taller
                """,clave(codigo,taller).addValue("vehiculo",vehiculo).addValue("usuario",usuario));
    }
    public void imprimir(List<String> codigos,long taller,long usuario) {
        jdbc.update("""
                UPDATE octano.codigo_qr SET impreso_en=CURRENT_TIMESTAMP,impreso_por=:usuario,fecha_modif=CURRENT_TIMESTAMP,usuario_modificacion_id=:usuario
                WHERE codigo IN (:codigos) AND taller_id=:taller
                """,clave("",taller).addValue("codigos",codigos).addValue("usuario",usuario));
    }
    public Optional<Mantenimiento> ultimoMantenimiento(long vehiculo,long taller,String tipo) {
        return jdbc.query("""
                SELECT o.kilometraje,o.aceite,o.filtros,
                  (o.fin_fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date AS fecha,
                  ARRAY(SELECT r.descripcion FROM octano.repuesto_orden r WHERE r.orden_id=o.id AND r.taller_id=o.taller_id
                    AND r.consumido AND (:tipo='SERVICE' OR lower(r.descripcion) SIMILAR TO '%(distribuci|correa|bomba|tensor)%') ORDER BY r.id) AS repuestos
                FROM octano.orden_trabajo o WHERE o.vehiculo_id=:vehiculo AND o.taller_id=:taller AND o.estado='FINALIZADO'
                  AND :tipo=ANY(o.tipos_trabajo) ORDER BY o.fin_fecha DESC,o.id DESC LIMIT 1
                """,clave("",taller).addValue("vehiculo",vehiculo).addValue("tipo",tipo),
                (rs,n)->new Mantenimiento(rs.getObject("fecha",java.time.LocalDate.class),rs.getObject("kilometraje",Integer.class),
                    rs.getString("aceite"),Arrays.asList((String[])rs.getArray("filtros").getArray()),
                    Arrays.asList((String[])rs.getArray("repuestos").getArray()))).stream().findFirst();
    }
}
