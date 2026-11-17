package com.octano.backend.vehiculos.repository;

import com.octano.backend.vehiculos.entity.Vehiculo;
import com.octano.backend.vehiculos.dto.VehiculosDatos.Titularidad;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VehiculoRepository {
    private static final String DESDE = """
             FROM octano.vehiculo v JOIN octano.cliente c ON c.id = v.cliente_id AND c.taller_id = v.taller_id
             JOIN octano.modelo mo ON mo.id = v.modelo_id JOIN octano.marca ma ON ma.id = mo.marca_id
             LEFT JOIN octano.version_vehiculo ve ON ve.id = v.version_id AND ve.modelo_id = v.modelo_id
             LEFT JOIN octano.codigo_qr qr ON qr.vehiculo_id = v.id AND qr.taller_id = v.taller_id
            """;
    private static final String CONSULTA = """
            SELECT v.id, v.taller_id, v.cliente_id, v.modelo_id, v.version_id, v.patente, v.anio, v.kilometraje,
                   v.activo, v.fecha_modif, v.usuario_modificacion_id, ma.id AS marca_id, ma.nombre AS marca,
                   mo.nombre AS modelo, ve.nombre AS version_nombre, c.nombre AS cliente_nombre, qr.codigo AS codigo_qr
            """ + DESDE;
    // Mismas reglas del buscador del frontend: acentos, separadores y palabras en cualquier orden.
    private static final String BUSQUEDA = """
             CROSS JOIN LATERAL (SELECT
                regexp_replace(normalize(lower(v.patente), NFD), U&'[\\0300-\\036f]', '', 'g') AS patente,
                regexp_replace(normalize(lower(ma.nombre), NFD), U&'[\\0300-\\036f]', '', 'g') AS marca,
                regexp_replace(normalize(lower(mo.nombre), NFD), U&'[\\0300-\\036f]', '', 'g') AS modelo,
                regexp_replace(normalize(lower(c.nombre), NFD), U&'[\\0300-\\036f]', '', 'g') AS cliente) n
             CROSS JOIN LATERAL (SELECT concat_ws(' ', n.patente, n.marca, n.modelo, n.cliente) AS texto,
                concat_ws(' ', regexp_replace(n.patente, '[^a-z0-9]', '', 'g'), regexp_replace(n.marca, '[^a-z0-9]', '', 'g'),
                regexp_replace(n.modelo, '[^a-z0-9]', '', 'g'), regexp_replace(n.cliente, '[^a-z0-9]', '', 'g')) AS limpio) b
            """;
    private static final String FILTRO = """
             WHERE v.taller_id = :taller AND (CAST(:cliente AS bigint) IS NULL OR v.cliente_id = :cliente)
             AND (CAST(:activo AS boolean) IS NULL OR v.activo = :activo) AND strpos(replace(v.patente, '-', ''), :patente) > 0
             AND (strpos(b.texto, :q) > 0 OR (length(:limpio) >= 2 AND strpos(b.limpio, :limpio) > 0)
                OR (cardinality(regexp_split_to_array(:q, '\\s+')) > 1 AND NOT EXISTS (
                    SELECT 1 FROM unnest(regexp_split_to_array(:q, '\\s+')) AS palabras(palabra)
                    WHERE NOT (strpos(b.texto, palabra) > 0 OR (length(regexp_replace(palabra, '[^a-z0-9]', '', 'g')) >= 2
                        AND strpos(b.limpio, regexp_replace(palabra, '[^a-z0-9]', '', 'g')) > 0)))))
            """;
    private static final RowMapper<Vehiculo> FILA = (rs, n) -> {
        Vehiculo v = new Vehiculo();
        v.setId(rs.getLong("id")); v.setTallerId(rs.getLong("taller_id")); v.setClienteId(rs.getLong("cliente_id"));
        v.setModeloId(rs.getLong("modelo_id")); v.setVersionId(rs.getObject("version_id", Long.class));
        v.setPatente(rs.getString("patente")); v.setAnio(rs.getInt("anio")); v.setKilometraje(rs.getObject("kilometraje", Integer.class));
        v.setActivo(rs.getBoolean("activo")); v.setFechaModif(rs.getTimestamp("fecha_modif").toInstant());
        v.setUsuarioModificacionId(rs.getLong("usuario_modificacion_id"));
        v.setMarcaId(rs.getLong("marca_id")); v.setMarca(rs.getString("marca")); v.setModelo(rs.getString("modelo"));
        v.setVersion(rs.getString("version_nombre")); v.setClienteNombre(rs.getString("cliente_nombre"));
        v.setCodigoQr(rs.getString("codigo_qr"));
        return v;
    };
    private final NamedParameterJdbcTemplate jdbc;
    public VehiculoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private MapSqlParameterSource clave(long id, long tallerId) {
        return new MapSqlParameterSource("id", id).addValue("taller", tallerId);
    }
    public Optional<Vehiculo> buscar(long id, long tallerId, boolean bloquear) {
        return jdbc.query(CONSULTA + " WHERE v.id = :id AND v.taller_id = :taller" + (bloquear ? " FOR UPDATE OF v" : ""),
                clave(id, tallerId), FILA).stream().findFirst();
    }
    private MapSqlParameterSource filtros(long taller, Long cliente, Boolean activo, String q, String patente) {
        return new MapSqlParameterSource("taller", taller).addValue("cliente", cliente).addValue("activo", activo)
                .addValue("q", q).addValue("limpio", q.replaceAll("[^a-z0-9]", "")).addValue("patente", patente);
    }
    public List<Vehiculo> listar(long tallerId, Long clienteId, Boolean activo, String q, String patente, int pagina, int tamano) {
        var params = filtros(tallerId, clienteId, activo, q, patente).addValue("limite", tamano).addValue("offset", (long) pagina * tamano);
        return jdbc.query(CONSULTA + BUSQUEDA + FILTRO + " ORDER BY v.id LIMIT :limite OFFSET :offset", params, FILA);
    }
    public long contar(long tallerId, Long clienteId, Boolean activo, String q, String patente) {
        return jdbc.queryForObject("SELECT count(*)" + DESDE + BUSQUEDA + FILTRO, filtros(tallerId, clienteId, activo, q, patente), Long.class);
    }
    public Vehiculo save(Vehiculo v) {
        var params = clave(v.getId() == null ? 0 : v.getId(), v.getTallerId()).addValue("cliente", v.getClienteId())
                .addValue("modelo", v.getModeloId()).addValue("version", v.getVersionId()).addValue("patente", v.getPatente())
                .addValue("anio", v.getAnio()).addValue("km", v.getKilometraje()).addValue("activo", v.getActivo())
                .addValue("fecha", Timestamp.from(v.getFechaModif())).addValue("usuario", v.getUsuarioModificacionId());
        if (v.getId() == null) {
            v.setId(jdbc.queryForObject("""
                    INSERT INTO octano.vehiculo(taller_id, cliente_id, modelo_id, version_id, patente, anio,
                        kilometraje, activo, fecha_modif, usuario_modificacion_id)
                    VALUES (:taller, :cliente, :modelo, :version, :patente, :anio, :km, :activo, :fecha, :usuario) RETURNING id
                    """, params, Long.class));
        } else {
            jdbc.update("""
                    UPDATE octano.vehiculo SET cliente_id = :cliente, modelo_id = :modelo, version_id = :version, patente = :patente,
                        anio = :anio, kilometraje = :km, activo = :activo, fecha_modif = :fecha, usuario_modificacion_id = :usuario
                    WHERE id = :id AND taller_id = :taller
                    """, params);
        }
        return v;
    }
    public void registrarTitularidad(Vehiculo v, Long anterior) {
        jdbc.update("""
                INSERT INTO octano.historial_titularidad(taller_id, vehiculo_id, cliente_anterior_id, cliente_nuevo_id, usuario_id, fecha)
                VALUES (:taller, :id, :anterior, :nuevo, :usuario, :fecha)
                """, clave(v.getId(), v.getTallerId()).addValue("anterior", anterior).addValue("nuevo", v.getClienteId())
                .addValue("usuario", v.getUsuarioModificacionId()).addValue("fecha", Timestamp.from(v.getFechaModif())));
    }
    public List<Titularidad> historial(long tallerId, long vehiculoId) {
        return jdbc.query("""
                SELECT id, cliente_anterior_id, cliente_nuevo_id, usuario_id, fecha FROM octano.historial_titularidad
                WHERE taller_id = :taller AND vehiculo_id = :id ORDER BY id
                """, clave(vehiculoId, tallerId), (rs, n) -> new Titularidad(rs.getLong("id"), rs.getObject("cliente_anterior_id", Long.class),
                rs.getLong("cliente_nuevo_id"), rs.getLong("usuario_id"), rs.getTimestamp("fecha").toInstant()));
    }
}
