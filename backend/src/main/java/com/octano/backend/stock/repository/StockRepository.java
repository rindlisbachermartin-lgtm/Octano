package com.octano.backend.stock.repository;

import com.octano.backend.stock.dto.StockDatos.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class StockRepository {
    private static final String CONSULTA = """
            SELECT id, tipo, cantidad, variacion, stock_anterior, stock_posterior, motivo, usuario_id, fecha
            FROM octano.movimiento_stock WHERE taller_id = ? AND repuesto_id = ?
            """;
    private static final RowMapper<Movimiento> FILA = (rs, n) -> new Movimiento(rs.getLong("id"), Tipo.valueOf(rs.getString("tipo")),
            rs.getBigDecimal("cantidad"), rs.getBigDecimal("variacion"), rs.getBigDecimal("stock_anterior"), rs.getBigDecimal("stock_posterior"),
            rs.getString("motivo"), rs.getLong("usuario_id"), rs.getTimestamp("fecha").toInstant());
    private final JdbcTemplate jdbc;
    public StockRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Movimiento> buscar(long tallerId, long repuestoId, long id) {
        return jdbc.query(CONSULTA + " AND id = ?", FILA, tallerId, repuestoId, id).stream().findFirst();
    }
    public List<Movimiento> listar(long tallerId, long repuestoId, int pagina, int tamano) {
        return jdbc.query(CONSULTA + " ORDER BY id DESC LIMIT ? OFFSET ?", FILA, tallerId, repuestoId, tamano, (long) pagina * tamano);
    }
    public long contar(long tallerId, long repuestoId) {
        return jdbc.queryForObject("SELECT count(*) FROM octano.movimiento_stock WHERE taller_id = ? AND repuesto_id = ?", Long.class, tallerId, repuestoId);
    }
    public Movimiento registrar(long tallerId, long repuestoId, long usuarioId, Solicitud s, BigDecimal anterior, BigDecimal posterior) {
        Long id = jdbc.queryForObject("""
                INSERT INTO octano.movimiento_stock(taller_id, repuesto_id, tipo, cantidad, variacion, stock_anterior, stock_posterior, motivo, usuario_id, fecha)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, tallerId, repuestoId, s.tipo().name(), s.cantidad(), posterior.subtract(anterior), anterior, posterior, s.motivo(), usuarioId);
        return buscar(tallerId, repuestoId, id).orElseThrow();
    }
}
