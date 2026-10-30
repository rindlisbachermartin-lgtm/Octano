package com.octano.backend.condicionesiva.repository;

import com.octano.backend.condicionesiva.entity.CondicionIva;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CondicionIvaRepository {
    private static final RowMapper<CondicionIva> FILA = (rs, numero) ->
            new CondicionIva(rs.getLong("id"), rs.getString("nombre"));
    private final JdbcTemplate jdbc;

    public CondicionIvaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CondicionIva> findAll() {
        return jdbc.query("SELECT id, nombre FROM octano.condicion_iva ORDER BY nombre", FILA);
    }
    public Optional<CondicionIva> findById(long id) {
        return jdbc.query("SELECT id, nombre FROM octano.condicion_iva WHERE id = ?", FILA, id).stream().findFirst();
    }
}
