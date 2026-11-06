package com.octano.backend.repuestos.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record Repuesto(Long id, Long tallerId, String codigoOem, String descripcion, String marca,
                       BigDecimal precioCosto, BigDecimal margen, BigDecimal precioPublico,
                       BigDecimal stock, BigDecimal stockMinimo, boolean activo,
                       Instant fechaModif, Long usuarioModificacionId) { }
