package com.octano.backend.aliquotasiva.repository;

import com.octano.backend.aliquotasiva.dto.AliquotaIvaRespuesta;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AliquotaIvaRepository {
    private final JdbcTemplate jdbc;
    public AliquotaIvaRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<AliquotaIvaRespuesta> findAll() {
        return jdbc.query("SELECT id,porcentaje FROM octano.aliquota_iva ORDER BY porcentaje,id",
                (rs,n)->new AliquotaIvaRespuesta(rs.getLong("id"),rs.getBigDecimal("porcentaje")));
    }
}
