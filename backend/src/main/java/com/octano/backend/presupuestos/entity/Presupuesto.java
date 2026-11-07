package com.octano.backend.presupuestos.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Presupuesto(Long id, Long tallerId, Long clienteId, Long vehiculoId, LocalDate fecha,
                          String descripcion, BigDecimal manoObra, BigDecimal porcentajeIva, BigDecimal totalRepuestos,
                          BigDecimal subtotal, BigDecimal montoIva, BigDecimal total, List<TipoTrabajo> tiposTrabajo,
                          Estado estado, Instant estadoFecha, Long estadoUsuarioId, Instant fechaModif, Long usuarioModificacionId,
                          String clienteNombre, String marca, String modelo, String patente, Long ordenId) {
    public enum Estado { PENDIENTE, ACEPTADO, RECHAZADO, ANULADO }
    public enum TipoTrabajo { SERVICE, DISTRIBUCION, OTRO }
    public record Detalle(int renglon, Long repuestoId, String codigoOem, String descripcion,
                          BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal subtotal) { }
}
