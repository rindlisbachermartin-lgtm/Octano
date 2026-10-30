package com.octano.backend.mecanicos.repository;

import com.octano.backend.mecanicos.entity.Mecanico;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MecanicoRepository {
    private static final String CONSULTA = "SELECT id, taller_id, nombre, apellido, telefono, calle, cuit, fecha_inicio, activo FROM octano.mecanico";
    private static final RowMapper<Mecanico> FILA = (rs, numero) -> {
        Mecanico mecanico = new Mecanico();
        mecanico.setId(rs.getLong("id")); mecanico.setTallerId(rs.getLong("taller_id"));
        mecanico.setNombre(rs.getString("nombre")); mecanico.setApellido(rs.getString("apellido"));
        mecanico.setTelefono(rs.getString("telefono")); mecanico.setCalle(rs.getString("calle")); mecanico.setCuit(rs.getString("cuit"));
        mecanico.setFechaInicio(rs.getObject("fecha_inicio", LocalDate.class)); mecanico.setActivo(rs.getBoolean("activo"));
        return mecanico;
    };
    private final JdbcTemplate jdbc;
    public MecanicoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Mecanico> findByIdAndTallerId(long id, long tallerId) {
        return jdbc.query(CONSULTA + " WHERE id = ? AND taller_id = ?", FILA, id, tallerId).stream().findFirst();
    }
    public Optional<Mecanico> findLockedByIdAndTallerId(long id, long tallerId) {
        return jdbc.query(CONSULTA + " WHERE id = ? AND taller_id = ? FOR UPDATE", FILA, id, tallerId).stream().findFirst();
    }
}
