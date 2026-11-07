package com.octano.backend.reportes.repository;

import com.octano.backend.reportes.dto.ReportesDatos.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReporteRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ReporteRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private MapSqlParameterSource parametros(long taller, Periodo periodo) {
        return new MapSqlParameterSource("taller",taller).addValue("desde",periodo.desde()).addValue("hasta",periodo.hasta());
    }
    private static final String COBROS="""
             FROM octano.cobro WHERE taller_id=:taller
             AND (fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date BETWEEN :desde AND :hasta
            """;
    public TotalesCobros cobros(long taller, Periodo periodo) {
        return jdbc.queryForObject("SELECT count(*) AS cantidad,coalesce(sum(importe),0) AS importe"+COBROS,parametros(taller,periodo),
                (rs,n)->new TotalesCobros(rs.getLong("cantidad"),rs.getBigDecimal("importe")));
    }
    public List<Dia> ingresosDiarios(long taller, Periodo periodo) {
        return jdbc.query("""
                WITH cobros AS (
                  SELECT (fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date AS dia,count(*) AS cantidad,sum(importe) AS importe
                """+COBROS+"""
                  GROUP BY 1
                ) SELECT d.dia::date AS fecha,coalesce(c.cantidad,0) AS cantidad,coalesce(c.importe,0) AS importe
                FROM generate_series(CAST(:desde AS timestamp),CAST(:hasta AS timestamp),interval '1 day') d(dia)
                LEFT JOIN cobros c ON c.dia=d.dia::date ORDER BY d.dia
                """,parametros(taller,periodo),(rs,n)->new Dia(rs.getObject("fecha",LocalDate.class),rs.getLong("cantidad"),rs.getBigDecimal("importe")));
    }
    public List<Medio> ingresosPorMedio(long taller, Periodo periodo) {
        return jdbc.query("SELECT medio_pago,count(*) AS cantidad,sum(importe) AS importe"+COBROS+" GROUP BY medio_pago ORDER BY medio_pago",
                parametros(taller,periodo),(rs,n)->new Medio(rs.getString("medio_pago"),rs.getLong("cantidad"),rs.getBigDecimal("importe")));
    }
    public Operacion operacion(long taller,LocalDate hoy) {
        return jdbc.queryForObject("""
                SELECT count(DISTINCT vehiculo_id) FILTER (WHERE estado IN ('EN_ESPERA','EN_PROCESO')) AS vehiculos,
                count(*) FILTER (WHERE estado='EN_ESPERA') AS espera,
                count(*) FILTER (WHERE estado='EN_PROCESO') AS proceso,
                count(*) FILTER (WHERE estado='PENDIENTE_INGRESO') AS pendientes,
                count(*) FILTER (WHERE estado='FINALIZADO' AND NOT EXISTS (
                  SELECT 1 FROM octano.comprobante c WHERE c.taller_id=o.taller_id AND c.orden_id=o.id)) AS sin_comprobante,
                (SELECT count(*) FROM octano.turno t WHERE t.taller_id=:taller AND t.fecha=:hoy AND t.estado<>'CANCELADO') AS turnos
                FROM octano.orden_trabajo o WHERE o.taller_id=:taller
                """,new MapSqlParameterSource("taller",taller).addValue("hoy",hoy),
                (rs,n)->new Operacion(rs.getLong("vehiculos"),rs.getLong("espera"),rs.getLong("proceso"),rs.getLong("pendientes"),rs.getLong("sin_comprobante"),rs.getLong("turnos")));
    }
    public Pendientes pendientes(long taller) {
        return jdbc.queryForObject("""
                SELECT count(*) AS cantidad,coalesce(sum(c.total),0) AS importe FROM octano.comprobante c
                WHERE c.taller_id=:taller AND c.estado='EMITIDO' AND NOT EXISTS (
                  SELECT 1 FROM octano.cobro b WHERE b.taller_id=c.taller_id AND b.comprobante_id=c.id)
                """,new MapSqlParameterSource("taller",taller),(rs,n)->new Pendientes(rs.getLong("cantidad"),rs.getBigDecimal("importe")));
    }
    public List<Cantidad> ordenesCreadas(long taller,Periodo periodo) {
        return jdbc.query("""
                SELECT estado,count(*) AS cantidad FROM octano.orden_trabajo WHERE taller_id=:taller
                AND (fecha_creacion AT TIME ZONE 'America/Argentina/Buenos_Aires')::date BETWEEN :desde AND :hasta
                GROUP BY estado ORDER BY estado
                """,parametros(taller,periodo),(rs,n)->new Cantidad(rs.getString("estado"),rs.getLong("cantidad")));
    }
    private static final String FINALIZADAS="""
             FROM octano.orden_trabajo o WHERE o.taller_id=:taller AND o.estado='FINALIZADO'
             AND (o.fin_fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date BETWEEN :desde AND :hasta
            """;
    public long finalizadas(long taller,Periodo periodo) {
        return jdbc.queryForObject("SELECT count(*)"+FINALIZADAS,parametros(taller,periodo),Long.class);
    }
    public List<Cantidad> finalizadasPorTipo(long taller,Periodo periodo) {
        return jdbc.query("SELECT tipo,count(*) AS cantidad FROM (SELECT DISTINCT o.id,unnest(o.tipos_trabajo) AS tipo"+FINALIZADAS+") trabajos GROUP BY tipo ORDER BY tipo",
                parametros(taller,periodo),(rs,n)->new Cantidad(rs.getString("tipo"),rs.getLong("cantidad")));
    }
    public List<Mecanico> finalizadasPorMecanico(long taller,Periodo periodo) {
        return jdbc.query("""
                SELECT o.mecanico_id,coalesce(nullif(trim(concat_ws(' ',m.nombre,m.apellido)),''),'Sin asignar') AS nombre,count(*) AS cantidad
                FROM octano.orden_trabajo o LEFT JOIN octano.mecanico m ON m.id=o.mecanico_id AND m.taller_id=o.taller_id
                WHERE o.taller_id=:taller AND o.estado='FINALIZADO'
                AND (o.fin_fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date BETWEEN :desde AND :hasta
                GROUP BY o.mecanico_id,m.nombre,m.apellido ORDER BY cantidad DESC,o.mecanico_id
                """,parametros(taller,periodo),(rs,n)->new Mecanico(rs.getObject("mecanico_id",Long.class),rs.getString("nombre"),rs.getLong("cantidad")));
    }
    private static final String STOCK_BAJO=" FROM octano.repuesto WHERE taller_id=:taller AND activo AND stock<=stock_minimo ";
    public long stockBajo(long taller) {
        return jdbc.queryForObject("SELECT count(*)"+STOCK_BAJO,new MapSqlParameterSource("taller",taller),Long.class);
    }
    public List<Alerta> alertas(long taller,int limite) {
        return jdbc.query("SELECT id,codigo_oem,descripcion,marca,stock,stock_minimo"+STOCK_BAJO+" ORDER BY stock,id LIMIT :limite",
                new MapSqlParameterSource("taller",taller).addValue("limite",limite),
                (rs,n)->new Alerta(rs.getLong("id"),rs.getString("codigo_oem"),rs.getString("descripcion"),rs.getString("marca"),rs.getBigDecimal("stock"),rs.getBigDecimal("stock_minimo")));
    }
    public List<Movimiento> movimientos(long taller,Periodo periodo,int limite) {
        // Se consulta el libro de stock: no se infiere el consumo a partir de órdenes editables.
        return jdbc.query("""
                SELECT r.id,r.codigo_oem,r.descripcion,r.marca,r.activo,
                coalesce(sum(m.cantidad) FILTER (WHERE m.tipo='ENTRADA'),0) AS entradas,
                coalesce(sum(m.cantidad) FILTER (WHERE m.tipo='SALIDA'),0) AS salidas,
                coalesce(sum(m.variacion) FILTER (WHERE m.tipo='AJUSTE'),0) AS ajuste,
                sum(m.variacion) AS variacion
                FROM octano.movimiento_stock m JOIN octano.repuesto r ON r.id=m.repuesto_id AND r.taller_id=m.taller_id
                WHERE m.taller_id=:taller AND (m.fecha AT TIME ZONE 'America/Argentina/Buenos_Aires')::date BETWEEN :desde AND :hasta
                GROUP BY r.id ORDER BY salidas DESC,r.id LIMIT :limite
                """,parametros(taller,periodo).addValue("limite",limite),
                (rs,n)->new Movimiento(rs.getLong("id"),rs.getString("codigo_oem"),rs.getString("descripcion"),rs.getString("marca"),rs.getBoolean("activo"),
                        rs.getBigDecimal("entradas"),rs.getBigDecimal("salidas"),rs.getBigDecimal("ajuste"),rs.getBigDecimal("variacion")));
    }
}
