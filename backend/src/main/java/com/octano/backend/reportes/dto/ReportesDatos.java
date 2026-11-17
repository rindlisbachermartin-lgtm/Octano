package com.octano.backend.reportes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportesDatos {
    private ReportesDatos() { }
    public record Periodo(LocalDate desde, LocalDate hasta) { }
    public record Operacion(long vehiculosEnTaller, long ordenesEnEspera, long ordenesEnProceso,
            long ordenesPendientesIngreso, long ordenesFinalizadasSinComprobante, long turnosHoy) { }
    public record Pendientes(long cantidad, BigDecimal importe) { }
    public record Resumen(LocalDate hoy, Periodo periodo, Operacion operacionActual,
            Pendientes comprobantesPorCobrar, long cantidadCobros, BigDecimal ingresos, long repuestosConStockBajo) { }
    public record Dia(LocalDate fecha, long cantidad, BigDecimal importe) { }
    public record Medio(String medioPago, long cantidad, BigDecimal importe) { }
    public record TotalesCobros(long cantidad, BigDecimal importe) { }
    public record Ingresos(Periodo periodo, long cantidadCobros, BigDecimal total, BigDecimal promedioPorCobro,
            List<Dia> porDia, List<Medio> porMedioPago) { }
    public record Cantidad(String nombre, long cantidad) { }
    public record Mecanico(Long mecanicoId, String nombre, long finalizadas) { }
    public record Trabajos(Periodo periodo, long creadas, long finalizadas,
            List<Cantidad> creadasPorEstadoActual, List<Cantidad> finalizadasPorTipo, List<Mecanico> finalizadasPorMecanico) { }
    public record Movimiento(long repuestoId, String codigoOem, String descripcion, String marca, boolean activo,
            BigDecimal entradas, BigDecimal salidas, BigDecimal ajusteNeto, BigDecimal variacionNeta) { }
    public record Alerta(long repuestoId, String codigoOem, String descripcion, String marca,
            BigDecimal stock, BigDecimal stockMinimo) { }
    public record Repuestos(Periodo periodo, int limite, List<Movimiento> movimientosPorRepuesto,
            long totalStockBajo, List<Alerta> stockBajoActual) { }
}
