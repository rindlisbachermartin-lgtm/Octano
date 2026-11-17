package com.octano.backend.turnos.repository;

import com.octano.backend.turnos.dto.TurnosDatos.*;
import com.octano.backend.turnos.entity.Turno;
import com.octano.backend.turnos.entity.Turno.Estado;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TurnoRepository {
    private static final String DESDE = """
             FROM octano.turno t JOIN octano.cliente c ON c.id = t.cliente_id AND c.taller_id = t.taller_id
             JOIN octano.vehiculo v ON v.id = t.vehiculo_id AND v.taller_id = t.taller_id
             JOIN octano.modelo mo ON mo.id = v.modelo_id JOIN octano.marca ma ON ma.id = mo.marca_id
             LEFT JOIN octano.version_vehiculo ve ON ve.id = v.version_id AND ve.modelo_id = v.modelo_id
             LEFT JOIN octano.orden_trabajo o ON o.turno_id = t.id AND o.taller_id = t.taller_id
            """;
    private static final String COLUMNAS = """
            SELECT t.id, t.taller_id, t.cliente_id, t.vehiculo_id, t.presupuesto_id, t.fecha, t.hora_inicio, t.hora_fin,
                   t.motivo_visita, t.estado, t.superposicion_autorizada, t.autorizada_por, t.autorizada_fecha,
                   t.fecha_modif, t.usuario_modificacion_id, c.nombre AS cliente_nombre, v.patente,
                   ma.nombre AS marca, mo.nombre AS modelo, ve.nombre AS version_nombre, v.anio, v.kilometraje, o.id AS orden_id
            """ + DESDE;
    private static final String FILTRO = """
             WHERE t.taller_id = :taller AND (CAST(:desde AS date) IS NULL OR t.fecha >= :desde)
             AND (CAST(:hasta AS date) IS NULL OR t.fecha <= :hasta)
             AND (CAST(:cliente AS bigint) IS NULL OR t.cliente_id = :cliente)
             AND (CAST(:vehiculo AS bigint) IS NULL OR t.vehiculo_id = :vehiculo)
             AND (CAST(:estado AS varchar) IS NULL OR t.estado = :estado)
            """;
    private static final RowMapper<Turno> FILA = (rs, n) -> {
        var autorizadaFecha = rs.getTimestamp("autorizada_fecha");
        return new Turno(rs.getLong("id"), rs.getLong("taller_id"), rs.getLong("cliente_id"), rs.getLong("vehiculo_id"), rs.getObject("presupuesto_id", Long.class),
                rs.getObject("fecha", LocalDate.class), rs.getObject("hora_inicio", LocalTime.class), rs.getObject("hora_fin", LocalTime.class),
                rs.getString("motivo_visita"), Estado.valueOf(rs.getString("estado")), rs.getBoolean("superposicion_autorizada"),
                rs.getObject("autorizada_por", Long.class), autorizadaFecha == null ? null : autorizadaFecha.toInstant(),
                rs.getTimestamp("fecha_modif").toInstant(), rs.getLong("usuario_modificacion_id"), rs.getString("cliente_nombre"),
                rs.getString("patente"), rs.getString("marca"), rs.getString("modelo"), rs.getString("version_nombre"),
                rs.getInt("anio"), rs.getObject("kilometraje", Integer.class), rs.getObject("orden_id", Long.class));
    };
    private static final String COLUMNAS_CONFLICTO = """
            SELECT t.id, t.vehiculo_id, t.hora_inicio, t.hora_fin, c.nombre AS cliente_nombre, v.patente, ma.nombre AS marca, mo.nombre AS modelo
            """ + DESDE;
    private static final RowMapper<Conflicto> FILA_CONFLICTO = (rs, n) -> new Conflicto(rs.getLong("id"), rs.getLong("vehiculo_id"),
            rs.getObject("hora_inicio", LocalTime.class), rs.getObject("hora_fin", LocalTime.class), rs.getString("cliente_nombre"),
            rs.getString("patente"), rs.getString("marca"), rs.getString("modelo"));
    private final NamedParameterJdbcTemplate jdbc;
    public TurnoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private MapSqlParameterSource clave(Long id, long taller) { return new MapSqlParameterSource("id", id).addValue("taller", taller); }
    public Optional<Turno> buscar(long id, long taller, boolean bloquear) {
        return jdbc.query(COLUMNAS + " WHERE t.id = :id AND t.taller_id = :taller" + (bloquear ? " FOR UPDATE OF t" : ""), clave(id, taller), FILA).stream().findFirst();
    }
    private MapSqlParameterSource filtros(long taller, LocalDate desde, LocalDate hasta, Long cliente, Long vehiculo, Estado estado) {
        return new MapSqlParameterSource("taller", taller).addValue("desde", desde).addValue("hasta", hasta)
                .addValue("cliente", cliente).addValue("vehiculo", vehiculo).addValue("estado", estado == null ? null : estado.name());
    }
    public List<Turno> listar(long taller, LocalDate desde, LocalDate hasta, Long cliente, Long vehiculo, Estado estado, int pagina, int tamano) {
        var params = filtros(taller, desde, hasta, cliente, vehiculo, estado).addValue("limite", tamano).addValue("offset", (long) pagina * tamano);
        return jdbc.query(COLUMNAS + FILTRO + " ORDER BY t.fecha, t.hora_inicio, t.id LIMIT :limite OFFSET :offset", params, FILA);
    }
    public long contar(long taller, LocalDate desde, LocalDate hasta, Long cliente, Long vehiculo, Estado estado) {
        return jdbc.queryForObject("SELECT count(*) FROM octano.turno t" + FILTRO, filtros(taller, desde, hasta, cliente, vehiculo, estado), Long.class);
    }
    private MapSqlParameterSource horario(Long excluir, long taller, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        return clave(excluir, taller).addValue("fecha", fecha).addValue("inicio", inicio).addValue("fin", fin);
    }
    public List<Conflicto> conflictos(long taller, LocalDate fecha, LocalTime inicio, LocalTime fin, Long excluir) {
        return jdbc.query(COLUMNAS_CONFLICTO + """
                 WHERE t.taller_id = :taller AND t.fecha = :fecha AND t.estado IN ('PROGRAMADO', 'CONFIRMADO', 'EN_TALLER')
                 AND t.hora_inicio < :fin AND t.hora_fin > :inicio AND (CAST(:id AS bigint) IS NULL OR t.id <> :id)
                 ORDER BY t.hora_inicio, t.id
                """, horario(excluir, taller, fecha, inicio, fin), FILA_CONFLICTO);
    }
    public List<Conflicto> turnosDelVehiculo(long taller, long vehiculo, LocalDate fecha, Long excluir) {
        return jdbc.query(COLUMNAS_CONFLICTO + """
                 WHERE t.taller_id = :taller AND t.vehiculo_id = :vehiculo AND t.fecha = :fecha
                 AND t.estado IN ('PROGRAMADO', 'CONFIRMADO', 'EN_TALLER') AND (CAST(:id AS bigint) IS NULL OR t.id <> :id)
                 ORDER BY t.hora_inicio, t.id
                """, clave(excluir, taller).addValue("vehiculo", vehiculo).addValue("fecha", fecha), FILA_CONFLICTO);
    }
    public boolean vehiculoConTurno(long taller, long vehiculo, LocalDate fecha, Long excluir) {
        return !turnosDelVehiculo(taller, vehiculo, fecha, excluir).isEmpty();
    }
    public long guardar(Long id, long taller, long usuario, Solicitud s, boolean autorizada) {
        var params = horario(id, taller, s.fecha(), s.horaInicio(), s.horaFin()).addValue("usuario", usuario)
                .addValue("cliente", s.clienteId()).addValue("vehiculo", s.vehiculoId()).addValue("presupuesto", s.presupuestoId()).addValue("motivo", s.motivoVisita()).addValue("autorizada", autorizada);
        if (id == null) {
            return jdbc.queryForObject("""
                    INSERT INTO octano.turno(taller_id, cliente_id, vehiculo_id, presupuesto_id, fecha, hora_inicio, hora_fin, motivo_visita,
                        superposicion_autorizada, autorizada_por, autorizada_fecha, fecha_modif, usuario_modificacion_id)
                    VALUES (:taller, :cliente, :vehiculo, :presupuesto, :fecha, :inicio, :fin, :motivo, :autorizada,
                        CASE WHEN :autorizada THEN :usuario ELSE NULL END,
                        CASE WHEN :autorizada THEN CURRENT_TIMESTAMP ELSE NULL END, CURRENT_TIMESTAMP, :usuario) RETURNING id
                    """, params, Long.class);
        }
        jdbc.update("""
                UPDATE octano.turno SET cliente_id = :cliente, vehiculo_id = :vehiculo, presupuesto_id = :presupuesto, fecha = :fecha, hora_inicio = :inicio,
                    hora_fin = :fin, motivo_visita = :motivo, superposicion_autorizada = :autorizada,
                    autorizada_por = CASE WHEN :autorizada THEN :usuario ELSE NULL END,
                    autorizada_fecha = CASE WHEN :autorizada THEN CURRENT_TIMESTAMP ELSE NULL END,
                    fecha_modif = CURRENT_TIMESTAMP, usuario_modificacion_id = :usuario WHERE id = :id AND taller_id = :taller
                """, params);
        return id;
    }
    public void estado(long id, long taller, long usuario, Estado estado) {
        jdbc.update("""
                UPDATE octano.turno SET estado = :estado, fecha_modif = CURRENT_TIMESTAMP, usuario_modificacion_id = :usuario
                WHERE id = :id AND taller_id = :taller
                """, clave(id, taller).addValue("estado", estado.name()).addValue("usuario", usuario));
    }
}
