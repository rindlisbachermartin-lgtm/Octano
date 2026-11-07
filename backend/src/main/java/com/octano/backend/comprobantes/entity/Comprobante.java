package com.octano.backend.comprobantes.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record Comprobante(Long id, Long tallerId, Long ordenId, Long clienteId, Long vehiculoId, LocalDate fecha,
        String descripcion, Estado estado, Long numero, BigDecimal manoObra, BigDecimal porcentajeIva,
        BigDecimal totalRepuestos, BigDecimal subtotal, BigDecimal montoIva, BigDecimal total,
        Parte emisor, Parte receptor, Vehiculo vehiculo, Instant creadoEn, Long creadoPor,
        Instant emitidoEn, Long emitidoPor, Instant fechaModif, Long usuarioModificacionId) {
    public enum Estado { BORRADOR, EMITIDO, COBRADO }
    public enum MedioPago { EFECTIVO, TRANSFERENCIA, TARJETA_DEBITO, TARJETA_CREDITO, MERCADO_PAGO }
    public record Parte(String nombre, String documento, String calle, Integer altura, String condicionIva, String telefono) { }
    public record Vehiculo(String patente, String marca, String modelo, String version, int anio) { }
    public record Item(int renglon, Long repuestoId, String codigoOem, String descripcion, BigDecimal cantidad,
                       BigDecimal precioUnitario, BigDecimal subtotal) { }
    public record Cobro(Long id, Long comprobanteId, Instant fecha, BigDecimal importe, MedioPago medioPago,
                        String observaciones, Long usuarioId) { }
}
