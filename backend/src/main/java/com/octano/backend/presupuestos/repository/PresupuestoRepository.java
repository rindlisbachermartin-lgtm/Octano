package com.octano.backend.presupuestos.repository;

import com.octano.backend.presupuestos.entity.Presupuesto;
import com.octano.backend.presupuestos.entity.Presupuesto.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PresupuestoRepository {
    private static final String DESDE = """
             FROM octano.presupuesto p JOIN octano.cliente c ON c.id = p.cliente_id AND c.taller_id = p.taller_id
             JOIN octano.vehiculo v ON v.id = p.vehiculo_id AND v.taller_id = p.taller_id
            """;
    private static final String COLUMNAS = """
            SELECT p.id, p.taller_id, p.cliente_id, p.vehiculo_id, p.fecha, p.descripcion, p.mano_obra, p.porcentaje_iva,
                   p.total_repuestos, p.subtotal, p.monto_iva, p.total, array_to_string(p.tipos_trabajo, ',') AS tipos,
                   p.estado, p.estado_fecha, p.estado_usuario_id, p.fecha_modif, p.usuario_modificacion_id
            """ + DESDE;
    private static final String FILTRO = """
             WHERE p.taller_id = :taller AND (CAST(:estado AS varchar) IS NULL OR p.estado = :estado)
             AND (CAST(:cliente AS bigint) IS NULL OR p.cliente_id = :cliente)
             AND (CAST(:vehiculo AS bigint) IS NULL OR p.vehiculo_id = :vehiculo)
             AND (CAST(:desde AS date) IS NULL OR p.fecha >= :desde) AND (CAST(:hasta AS date) IS NULL OR p.fecha <= :hasta)
             AND strpos(lower(p.descripcion || ' ' || c.nombre || ' ' || v.patente || ' ' || p.id::text), lower(:q)) > 0
            """;
    private static final RowMapper<Presupuesto> FILA = (rs, n) -> new Presupuesto(rs.getLong("id"), rs.getLong("taller_id"),
            rs.getLong("cliente_id"), rs.getLong("vehiculo_id"), rs.getObject("fecha", LocalDate.class), rs.getString("descripcion"),
            rs.getBigDecimal("mano_obra"), rs.getBigDecimal("porcentaje_iva"), rs.getBigDecimal("total_repuestos"), rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("monto_iva"), rs.getBigDecimal("total"), Arrays.stream(rs.getString("tipos").split(",")).map(TipoTrabajo::valueOf).toList(),
            Estado.valueOf(rs.getString("estado")), rs.getTimestamp("estado_fecha").toInstant(), rs.getLong("estado_usuario_id"),
            rs.getTimestamp("fecha_modif").toInstant(), rs.getLong("usuario_modificacion_id"));
    private final NamedParameterJdbcTemplate jdbc;
    public PresupuestoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private MapSqlParameterSource clave(Long id, long taller) { return new MapSqlParameterSource("id", id).addValue("taller", taller); }
    public Optional<Presupuesto> buscar(long id, long taller, boolean bloquear) {
        return jdbc.query(COLUMNAS + " WHERE p.id = :id AND p.taller_id = :taller" + (bloquear ? " FOR UPDATE OF p" : ""), clave(id, taller), FILA).stream().findFirst();
    }
    private MapSqlParameterSource filtros(long taller, String q, Estado estado, Long cliente, Long vehiculo, LocalDate desde, LocalDate hasta) {
        return new MapSqlParameterSource("taller", taller).addValue("q", q).addValue("estado", estado == null ? null : estado.name())
                .addValue("cliente", cliente).addValue("vehiculo", vehiculo).addValue("desde", desde).addValue("hasta", hasta);
    }
    public List<Presupuesto> listar(long taller, String q, Estado estado, Long cliente, Long vehiculo, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        var params = filtros(taller, q, estado, cliente, vehiculo, desde, hasta).addValue("limite", tamano).addValue("offset", (long) pagina * tamano);
        return jdbc.query(COLUMNAS + FILTRO + " ORDER BY p.fecha DESC, p.id DESC LIMIT :limite OFFSET :offset", params, FILA);
    }
    public long contar(long taller, String q, Estado estado, Long cliente, Long vehiculo, LocalDate desde, LocalDate hasta) {
        return jdbc.queryForObject("SELECT count(*)" + DESDE + FILTRO, filtros(taller, q, estado, cliente, vehiculo, desde, hasta), Long.class);
    }
    public List<Detalle> items(long id, long taller) {
        return jdbc.query("""
                SELECT renglon, repuesto_id, codigo_oem, descripcion, cantidad, precio_unitario, subtotal
                FROM octano.detalle_presupuesto WHERE presupuesto_id = :id AND taller_id = :taller ORDER BY renglon
                """, clave(id, taller), (rs, n) -> new Detalle(rs.getInt("renglon"), rs.getObject("repuesto_id", Long.class),
                rs.getString("codigo_oem"), rs.getString("descripcion"), rs.getBigDecimal("cantidad"), rs.getBigDecimal("precio_unitario"), rs.getBigDecimal("subtotal")));
    }
    public long guardar(Presupuesto p, long usuario, List<Detalle> items) {
        var params = clave(p.id(), p.tallerId()).addValue("cliente", p.clienteId()).addValue("vehiculo", p.vehiculoId())
                .addValue("fecha", p.fecha()).addValue("descripcion", p.descripcion()).addValue("manoObra", p.manoObra())
                .addValue("iva", p.porcentajeIva()).addValue("repuestos", p.totalRepuestos()).addValue("subtotal", p.subtotal())
                .addValue("impuesto", p.montoIva()).addValue("total", p.total()).addValue("usuario", usuario)
                .addValue("tipos", String.join(",", p.tiposTrabajo().stream().map(Enum::name).toList()));
        long id;
        if (p.id() == null) {
            id = jdbc.queryForObject("""
                    INSERT INTO octano.presupuesto(taller_id, cliente_id, vehiculo_id, fecha, descripcion, mano_obra, porcentaje_iva,
                        total_repuestos, subtotal, monto_iva, total, tipos_trabajo, estado_usuario_id, usuario_modificacion_id)
                    VALUES (:taller, :cliente, :vehiculo, :fecha, :descripcion, :manoObra, :iva, :repuestos, :subtotal, :impuesto,
                        :total, string_to_array(:tipos, ','), :usuario, :usuario) RETURNING id
                    """, params, Long.class);
        } else {
            id = p.id();
            jdbc.update("""
                    UPDATE octano.presupuesto SET cliente_id = :cliente, vehiculo_id = :vehiculo, descripcion = :descripcion,
                        mano_obra = :manoObra, porcentaje_iva = :iva, total_repuestos = :repuestos, subtotal = :subtotal,
                        monto_iva = :impuesto, total = :total, tipos_trabajo = string_to_array(:tipos, ','),
                        fecha_modif = CURRENT_TIMESTAMP, usuario_modificacion_id = :usuario WHERE id = :id AND taller_id = :taller
                    """, params);
            jdbc.update("DELETE FROM octano.detalle_presupuesto WHERE presupuesto_id = :id AND taller_id = :taller", clave(id, p.tallerId()));
        }
        if (!items.isEmpty()) {
            long presupuestoId = id;
            var filas = items.stream().map(d -> clave(presupuestoId, p.tallerId()).addValue("renglon", d.renglon())
                    .addValue("repuesto", d.repuestoId()).addValue("codigo", d.codigoOem()).addValue("descripcion", d.descripcion())
                    .addValue("cantidad", d.cantidad()).addValue("precio", d.precioUnitario()).addValue("subtotal", d.subtotal()))
                    .toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    INSERT INTO octano.detalle_presupuesto(taller_id, presupuesto_id, renglon, repuesto_id, codigo_oem, descripcion, cantidad, precio_unitario, subtotal)
                    VALUES (:taller, :id, :renglon, :repuesto, :codigo, :descripcion, :cantidad, :precio, :subtotal)
                    """, filas);
        }
        return id;
    }
    public Optional<Long> turnoActivo(long id, long taller, Long excluir) {
        return jdbc.query("""
                SELECT id FROM octano.turno WHERE presupuesto_id = :id AND taller_id = :taller
                AND estado IN ('PROGRAMADO', 'CONFIRMADO', 'EN_TALLER') AND (CAST(:excluir AS bigint) IS NULL OR id <> :excluir)
                """, clave(id, taller).addValue("excluir", excluir), (rs, n) -> rs.getLong("id")).stream().findFirst();
    }
    public void estado(long id, long taller, long usuario, Estado estado) {
        jdbc.update("""
                UPDATE octano.presupuesto SET estado = :estado, estado_fecha = CURRENT_TIMESTAMP, estado_usuario_id = :usuario,
                    fecha_modif = CURRENT_TIMESTAMP, usuario_modificacion_id = :usuario WHERE id = :id AND taller_id = :taller
                """, clave(id, taller).addValue("usuario", usuario).addValue("estado", estado.name()));
    }
    public boolean vehiculoEnTaller(long id, long taller) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM octano.turno WHERE presupuesto_id = :id AND taller_id = :taller AND estado = 'EN_TALLER')",
                clave(id, taller), Boolean.class);
    }
    public void cancelarTurnos(long id, long taller, long usuario) {
        jdbc.update("""
                UPDATE octano.turno SET estado = 'CANCELADO', fecha_modif = CURRENT_TIMESTAMP, usuario_modificacion_id = :usuario
                WHERE presupuesto_id = :id AND taller_id = :taller AND estado IN ('PROGRAMADO', 'CONFIRMADO')
                """, clave(id, taller).addValue("usuario", usuario));
    }
}
