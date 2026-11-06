CREATE TABLE octano.presupuesto (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    cliente_id BIGINT NOT NULL,
    vehiculo_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    descripcion VARCHAR(2000) NOT NULL CHECK (btrim(descripcion) <> ''),
    mano_obra NUMERIC(18,2) NOT NULL CHECK (mano_obra >= 0),
    porcentaje_iva NUMERIC(5,2) NOT NULL CHECK (porcentaje_iva BETWEEN 0 AND 100),
    total_repuestos NUMERIC(18,2) NOT NULL CHECK (total_repuestos >= 0),
    subtotal NUMERIC(18,2) NOT NULL CHECK (subtotal = total_repuestos + mano_obra),
    monto_iva NUMERIC(18,2) NOT NULL CHECK (monto_iva = round(subtotal * porcentaje_iva / 100, 2)),
    total NUMERIC(18,2) NOT NULL CHECK (total = subtotal + monto_iva),
    tipos_trabajo TEXT[] NOT NULL CHECK (cardinality(tipos_trabajo) BETWEEN 1 AND 3)
        CHECK (tipos_trabajo <@ ARRAY['SERVICE', 'DISTRIBUCION', 'OTRO']),
    estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'ACEPTADO', 'RECHAZADO', 'ANULADO')),
    estado_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado_usuario_id BIGINT NOT NULL,
    fecha_modif TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_modificacion_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id, cliente_id) REFERENCES octano.cliente(taller_id, id),
    FOREIGN KEY (taller_id, vehiculo_id) REFERENCES octano.vehiculo(taller_id, id),
    FOREIGN KEY (taller_id, estado_usuario_id) REFERENCES octano.usuario(taller_id, id),
    FOREIGN KEY (taller_id, usuario_modificacion_id) REFERENCES octano.usuario(taller_id, id),
    UNIQUE (taller_id, id)
);
CREATE INDEX presupuesto_listado_idx ON octano.presupuesto(taller_id, fecha, id);

CREATE TABLE octano.detalle_presupuesto (
    taller_id BIGINT NOT NULL,
    presupuesto_id BIGINT NOT NULL,
    renglon INTEGER NOT NULL CHECK (renglon BETWEEN 1 AND 200),
    repuesto_id BIGINT,
    codigo_oem VARCHAR(80),
    descripcion VARCHAR(250) NOT NULL CHECK (btrim(descripcion) <> ''),
    cantidad NUMERIC(15,3) NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(18,2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal NUMERIC(18,2) NOT NULL CHECK (subtotal = round(cantidad * precio_unitario, 2)),
    PRIMARY KEY (taller_id, presupuesto_id, renglon),
    FOREIGN KEY (taller_id, presupuesto_id) REFERENCES octano.presupuesto(taller_id, id),
    FOREIGN KEY (taller_id, repuesto_id) REFERENCES octano.repuesto(taller_id, id)
);
CREATE UNIQUE INDEX presupuesto_repuesto_unico ON octano.detalle_presupuesto(taller_id, presupuesto_id, repuesto_id)
    WHERE repuesto_id IS NOT NULL;

ALTER TABLE octano.turno ADD COLUMN presupuesto_id BIGINT;
ALTER TABLE octano.turno ADD CONSTRAINT turno_presupuesto_fk
    FOREIGN KEY (taller_id, presupuesto_id) REFERENCES octano.presupuesto(taller_id, id);
CREATE UNIQUE INDEX presupuesto_turno_activo_unico ON octano.turno(taller_id, presupuesto_id)
    WHERE presupuesto_id IS NOT NULL AND estado IN ('PROGRAMADO', 'CONFIRMADO');
