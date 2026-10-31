package com.octano.backend.vehiculos.repository;

import com.octano.backend.vehiculos.entity.Vehiculo;
import com.octano.backend.vehiculos.dto.VehiculosDatos.Titularidad;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class VehiculoRepository {
    private static final String CONSULTA = """
            SELECT id, taller_id, cliente_id, modelo_id, version_id, patente, anio, kilometraje,
                   activo, fecha_modif, usuario_modificacion_id FROM octano.vehiculo
            """;
    private static final String FILTRO = """
             WHERE taller_id = ? AND (?::bigint IS NULL OR cliente_id = ?)
             AND (?::boolean IS NULL OR activo = ?) AND strpos(replace(patente, '-', ''), ?) > 0
            """;
    private static final RowMapper<Vehiculo> FILA = (rs, n) -> {
        Vehiculo v = new Vehiculo();
        v.setId(rs.getLong("id")); v.setTallerId(rs.getLong("taller_id")); v.setClienteId(rs.getLong("cliente_id"));
        v.setModeloId(rs.getLong("modelo_id")); v.setVersionId(rs.getObject("version_id", Long.class));
        v.setPatente(rs.getString("patente")); v.setAnio(rs.getInt("anio")); v.setKilometraje(rs.getInt("kilometraje"));
        v.setActivo(rs.getBoolean("activo")); v.setFechaModif(rs.getTimestamp("fecha_modif").toInstant());
        v.setUsuarioModificacionId(rs.getLong("usuario_modificacion_id"));
        return v;
    };
    private final JdbcTemplate jdbc;
    public VehiculoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Vehiculo> buscar(long id, long tallerId, boolean bloquear) {
        return jdbc.query(CONSULTA + " WHERE id = ? AND taller_id = ?" + (bloquear ? " FOR UPDATE" : ""), FILA, id, tallerId).stream().findFirst();
    }
    public List<Vehiculo> listar(long tallerId, Long clienteId, Boolean activo, String patente, int pagina, int tamano) {
        return jdbc.query(CONSULTA + FILTRO + " ORDER BY id LIMIT ? OFFSET ?", FILA,
                tallerId, clienteId, clienteId, activo, activo, patente, tamano, (long) pagina * tamano);
    }
    public long contar(long tallerId, Long clienteId, Boolean activo, String patente) {
        return jdbc.queryForObject("SELECT count(*) FROM octano.vehiculo" + FILTRO, Long.class,
                tallerId, clienteId, clienteId, activo, activo, patente);
    }
    public Vehiculo save(Vehiculo v) {
        if (v.getId() == null) {
            v.setId(jdbc.queryForObject("""
                    INSERT INTO octano.vehiculo(taller_id, cliente_id, modelo_id, version_id, patente, anio,
                        kilometraje, activo, fecha_modif, usuario_modificacion_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, v.getTallerId(), v.getClienteId(), v.getModeloId(), v.getVersionId(), v.getPatente(),
                    v.getAnio(), v.getKilometraje(), v.getActivo(), Timestamp.from(v.getFechaModif()), v.getUsuarioModificacionId()));
        } else {
            jdbc.update("""
                    UPDATE octano.vehiculo SET cliente_id = ?, modelo_id = ?, version_id = ?, patente = ?, anio = ?,
                        kilometraje = ?, activo = ?, fecha_modif = ?, usuario_modificacion_id = ? WHERE id = ? AND taller_id = ?
                    """, v.getClienteId(), v.getModeloId(), v.getVersionId(), v.getPatente(), v.getAnio(), v.getKilometraje(),
                    v.getActivo(), Timestamp.from(v.getFechaModif()), v.getUsuarioModificacionId(), v.getId(), v.getTallerId());
        }
        return v;
    }
    public void registrarTitularidad(Vehiculo v, Long anterior) {
        jdbc.update("""
                INSERT INTO octano.historial_titularidad(taller_id, vehiculo_id, cliente_anterior_id, cliente_nuevo_id, usuario_id, fecha)
                VALUES (?, ?, ?, ?, ?, ?)
                """, v.getTallerId(), v.getId(), anterior, v.getClienteId(), v.getUsuarioModificacionId(), Timestamp.from(v.getFechaModif()));
    }
    public List<Titularidad> historial(long tallerId, long vehiculoId) {
        return jdbc.query("""
                SELECT id, cliente_anterior_id, cliente_nuevo_id, usuario_id, fecha FROM octano.historial_titularidad
                WHERE taller_id = ? AND vehiculo_id = ? ORDER BY id
                """, (rs, n) -> new Titularidad(rs.getLong("id"), rs.getObject("cliente_anterior_id", Long.class),
                rs.getLong("cliente_nuevo_id"), rs.getLong("usuario_id"), rs.getTimestamp("fecha").toInstant()), tallerId, vehiculoId);
    }
}
