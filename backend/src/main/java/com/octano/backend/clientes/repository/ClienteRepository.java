package com.octano.backend.clientes.repository;

import com.octano.backend.clientes.entity.Cliente;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClienteRepository {
    private static final String CONSULTA = """
            SELECT id, taller_id, nombre, documento, telefono, email, calle, altura,
                   condicion_iva_id, activo, fecha_modif, usuario_modificacion_id FROM octano.cliente
            """;
    private static final String FILTRO = """
             WHERE taller_id = ? AND (?::boolean IS NULL OR activo = ?)
             AND (strpos(lower(nombre), lower(?)) > 0 OR strpos(documento, nullif(?, '')) > 0
                  OR strpos(lower(coalesce(email, '')), lower(?)) > 0)
            """;
    private static final RowMapper<Cliente> FILA = (rs, numero) -> {
        Cliente cliente = new Cliente();
        cliente.setId(rs.getLong("id")); cliente.setTallerId(rs.getLong("taller_id"));
        cliente.setNombre(rs.getString("nombre")); cliente.setDocumento(rs.getString("documento"));
        cliente.setTelefono(rs.getString("telefono")); cliente.setEmail(rs.getString("email"));
        cliente.setCalle(rs.getString("calle")); cliente.setAltura(rs.getObject("altura", Integer.class));
        cliente.setCondicionIvaId(rs.getLong("condicion_iva_id")); cliente.setActivo(rs.getBoolean("activo"));
        cliente.setFechaModif(rs.getTimestamp("fecha_modif").toInstant());
        cliente.setUsuarioModificacionId(rs.getLong("usuario_modificacion_id"));
        return cliente;
    };
    private final JdbcTemplate jdbc;
    public ClienteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Cliente> findByIdAndTallerId(long id, long tallerId, boolean bloquear) {
        return jdbc.query(CONSULTA + " WHERE id = ? AND taller_id = ?" + (bloquear ? " FOR UPDATE" : ""), FILA, id, tallerId)
                .stream().findFirst();
    }
    public List<Cliente> buscar(long tallerId, String q, Boolean activo, int pagina, int tamano) {
        return jdbc.query(CONSULTA + FILTRO + " ORDER BY nombre, id LIMIT ? OFFSET ?", FILA,
                tallerId, activo, activo, q, q.replaceAll("[ .-]", ""), q, tamano, (long) pagina * tamano);
    }
    public long contar(long tallerId, String q, Boolean activo) {
        return jdbc.queryForObject("SELECT count(*) FROM octano.cliente" + FILTRO, Long.class,
                tallerId, activo, activo, q, q.replaceAll("[ .-]", ""), q);
    }
    public Cliente save(Cliente cliente) {
        if (cliente.getId() == null) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO octano.cliente(taller_id, nombre, documento, telefono, email, calle, altura,
                        condicion_iva_id, activo, fecha_modif, usuario_modificacion_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, cliente.getTallerId(), cliente.getNombre(), cliente.getDocumento(), cliente.getTelefono(),
                    cliente.getEmail(), cliente.getCalle(), cliente.getAltura(), cliente.getCondicionIvaId(), cliente.getActivo(),
                    Timestamp.from(cliente.getFechaModif()), cliente.getUsuarioModificacionId());
            cliente.setId(id);
        } else {
            jdbc.update("""
                    UPDATE octano.cliente SET nombre = ?, documento = ?, telefono = ?, email = ?, calle = ?, altura = ?,
                        condicion_iva_id = ?, activo = ?, fecha_modif = ?, usuario_modificacion_id = ?
                    WHERE id = ? AND taller_id = ?
                    """, cliente.getNombre(), cliente.getDocumento(), cliente.getTelefono(), cliente.getEmail(),
                    cliente.getCalle(), cliente.getAltura(), cliente.getCondicionIvaId(), cliente.getActivo(),
                    Timestamp.from(cliente.getFechaModif()), cliente.getUsuarioModificacionId(), cliente.getId(), cliente.getTallerId());
        }
        return cliente;
    }
}
