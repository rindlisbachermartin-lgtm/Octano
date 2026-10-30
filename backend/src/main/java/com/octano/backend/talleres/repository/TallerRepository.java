package com.octano.backend.talleres.repository;

import com.octano.backend.condicionesiva.entity.CondicionIva;
import com.octano.backend.talleres.entity.Taller;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TallerRepository {
    private static final String CONSULTA = """
            SELECT t.id, t.nombre, t.calle, t.altura, t.telefono, t.cuit, t.ingresos_brutos,
                   t.inicio_actividades, t.condicion_iva_id, c.nombre AS condicion_nombre
            FROM octano.taller t JOIN octano.condicion_iva c ON c.id = t.condicion_iva_id
            """;
    private static final RowMapper<Taller> FILA = (rs, numero) -> {
        Taller taller = new Taller(rs.getString("nombre"), rs.getString("calle"), rs.getInt("altura"),
                rs.getString("telefono"), rs.getString("cuit"), rs.getString("ingresos_brutos"),
                rs.getObject("inicio_actividades", LocalDate.class),
                new CondicionIva(rs.getLong("condicion_iva_id"), rs.getString("condicion_nombre")));
        taller.setId(rs.getLong("id"));
        return taller;
    };
    private final JdbcTemplate jdbc;
    public TallerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Taller> findById(long id) {
        return jdbc.query(CONSULTA + " WHERE t.id = ?", FILA, id).stream().findFirst();
    }
    public Optional<Taller> findLockedById(long id) {
        return jdbc.query(CONSULTA + " WHERE t.id = ? FOR UPDATE OF t", FILA, id).stream().findFirst();
    }
    public Taller save(Taller taller) {
        if (taller.getId() == null) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO octano.taller(nombre, calle, altura, telefono, cuit, ingresos_brutos, inicio_actividades, condicion_iva_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, taller.getNombre(), taller.getCalle(), taller.getAltura(), taller.getTelefono(),
                    taller.getCuit(), taller.getIngresosBrutos(), taller.getInicioActividades(), taller.getCondicionIva().getId());
            taller.setId(id);
        } else {
            jdbc.update("""
                    UPDATE octano.taller SET nombre = ?, calle = ?, altura = ?, telefono = ?, cuit = ?,
                        ingresos_brutos = ?, inicio_actividades = ?, condicion_iva_id = ? WHERE id = ?
                    """, taller.getNombre(), taller.getCalle(), taller.getAltura(), taller.getTelefono(),
                    taller.getCuit(), taller.getIngresosBrutos(), taller.getInicioActividades(), taller.getCondicionIva().getId(), taller.getId());
        }
        return taller;
    }
}
