package com.octano.backend.repuestos.repository;

import com.octano.backend.repuestos.dto.RepuestosDatos.*;
import com.octano.backend.repuestos.entity.Repuesto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepuestoRepository {
    private static final String COLUMNAS = """
            SELECT r.id, r.taller_id, r.codigo_oem, r.descripcion, r.marca, r.precio_costo, r.margen,
                   r.precio_publico, r.stock, r.stock_minimo, r.activo, r.fecha_modif, r.usuario_modificacion_id
            FROM octano.repuesto r
            """;
    private static final String FILTRO = """
             WHERE r.taller_id = :taller
             AND (CAST(:activo AS boolean) IS NULL OR r.activo = :activo)
             AND (CAST(:bajo AS boolean) IS NULL OR (r.stock <= r.stock_minimo) = :bajo)
             AND strpos(lower(r.codigo_oem || ' ' || r.descripcion || ' ' || r.marca), lower(:q)) > 0
             AND (CAST(:modelo AS bigint) IS NULL OR EXISTS (
                 SELECT 1 FROM octano.compatibilidad_repuesto c WHERE c.taller_id = r.taller_id AND c.repuesto_id = r.id
                 AND c.modelo_id = :modelo AND (CAST(:version AS bigint) IS NULL OR c.version_id IS NULL OR c.version_id = :version)))
            """;
    private static final RowMapper<Repuesto> FILA = (rs, n) -> new Repuesto(rs.getLong("id"), rs.getLong("taller_id"),
            rs.getString("codigo_oem"), rs.getString("descripcion"), rs.getString("marca"), rs.getBigDecimal("precio_costo"),
            rs.getBigDecimal("margen"), rs.getBigDecimal("precio_publico"), rs.getBigDecimal("stock"), rs.getBigDecimal("stock_minimo"),
            rs.getBoolean("activo"), rs.getTimestamp("fecha_modif").toInstant(), rs.getLong("usuario_modificacion_id"));
    private final NamedParameterJdbcTemplate jdbc;
    public RepuestoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private MapSqlParameterSource clave(long id, long tallerId) {
        return new MapSqlParameterSource("id", id).addValue("taller", tallerId);
    }
    public Optional<Repuesto> buscar(long id, long tallerId, boolean bloquear) {
        return jdbc.query(COLUMNAS + " WHERE r.id = :id AND r.taller_id = :taller" + (bloquear ? " FOR UPDATE" : ""),
                clave(id, tallerId), FILA).stream().findFirst();
    }
    private MapSqlParameterSource filtros(long tallerId, String q, Boolean activo, Boolean bajo, Long modelo, Long version) {
        return new MapSqlParameterSource("taller", tallerId).addValue("q", q).addValue("activo", activo)
                .addValue("bajo", bajo).addValue("modelo", modelo).addValue("version", version);
    }
    public List<Repuesto> listar(long tallerId, String q, Boolean activo, Boolean bajo, Long modelo, Long version, int pagina, int tamano) {
        var params = filtros(tallerId, q, activo, bajo, modelo, version).addValue("limite", tamano).addValue("offset", (long) pagina * tamano);
        return jdbc.query(COLUMNAS + FILTRO + " ORDER BY r.descripcion, r.id LIMIT :limite OFFSET :offset", params, FILA);
    }
    public long contar(long tallerId, String q, Boolean activo, Boolean bajo, Long modelo, Long version) {
        return jdbc.queryForObject("SELECT count(*) FROM octano.repuesto r" + FILTRO,
                filtros(tallerId, q, activo, bajo, modelo, version), Long.class);
    }
    public long guardar(Long id, long tallerId, long usuarioId, Solicitud s, BigDecimal precioPublico) {
        var params = new MapSqlParameterSource("id", id).addValue("taller", tallerId).addValue("usuario", usuarioId)
                .addValue("codigo", s.codigoOem()).addValue("descripcion", s.descripcion()).addValue("marca", s.marca())
                .addValue("costo", s.precioCosto()).addValue("margen", s.margen()).addValue("precio", precioPublico).addValue("minimo", s.stockMinimo());
        if (id == null) {
            return jdbc.queryForObject("""
                    INSERT INTO octano.repuesto(taller_id, codigo_oem, descripcion, marca, precio_costo, margen,
                        precio_publico, stock_minimo, fecha_modif, usuario_modificacion_id)
                    VALUES (:taller, :codigo, :descripcion, :marca, :costo, :margen, :precio, :minimo, CURRENT_TIMESTAMP, :usuario) RETURNING id
                    """, params, Long.class);
        }
        jdbc.update("""
                UPDATE octano.repuesto SET codigo_oem = :codigo, descripcion = :descripcion, marca = :marca,
                    precio_costo = :costo, margen = :margen, precio_publico = :precio, stock_minimo = :minimo,
                    fecha_modif = CURRENT_TIMESTAMP, usuario_modificacion_id = :usuario
                WHERE id = :id AND taller_id = :taller
                """, params);
        return id;
    }
    public void estado(long id, long tallerId, long usuarioId, boolean activo) {
        jdbc.update("""
                UPDATE octano.repuesto SET activo = :activo, fecha_modif = CURRENT_TIMESTAMP, usuario_modificacion_id = :usuario
                WHERE id = :id AND taller_id = :taller
                """, clave(id, tallerId).addValue("activo", activo).addValue("usuario", usuarioId));
    }
    public void saldo(long id, long tallerId, long usuarioId, BigDecimal stock) {
        jdbc.update("""
                UPDATE octano.repuesto SET stock = :stock, fecha_modif = CURRENT_TIMESTAMP, usuario_modificacion_id = :usuario
                WHERE id = :id AND taller_id = :taller
                """, clave(id, tallerId).addValue("stock", stock).addValue("usuario", usuarioId));
    }
    public List<Compatibilidad> compatibilidades(long id, long tallerId) {
        return jdbc.query("SELECT id, modelo_id, version_id FROM octano.compatibilidad_repuesto WHERE repuesto_id = :id AND taller_id = :taller ORDER BY id",
                clave(id, tallerId), (rs, n) -> new Compatibilidad(rs.getLong("id"), rs.getLong("modelo_id"), rs.getObject("version_id", Long.class)));
    }
    public Compatibilidad agregar(long id, long tallerId, CompatibilidadSolicitud s) {
        Long compatibilidadId = jdbc.queryForObject("""
                INSERT INTO octano.compatibilidad_repuesto(taller_id, repuesto_id, modelo_id, version_id)
                VALUES (:taller, :id, :modelo, :version) RETURNING id
                """, clave(id, tallerId).addValue("modelo", s.modeloId()).addValue("version", s.versionId()), Long.class);
        return new Compatibilidad(compatibilidadId, s.modeloId(), s.versionId());
    }
    public boolean quitar(long id, long tallerId, long compatibilidadId) {
        return jdbc.update("DELETE FROM octano.compatibilidad_repuesto WHERE id = :compatibilidad AND repuesto_id = :id AND taller_id = :taller",
                clave(id, tallerId).addValue("compatibilidad", compatibilidadId)) > 0;
    }
}
