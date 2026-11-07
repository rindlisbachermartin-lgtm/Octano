CREATE TABLE octano.comprobante (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    orden_id BIGINT,
    cliente_id BIGINT NOT NULL,
    vehiculo_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    descripcion VARCHAR(2000) NOT NULL CHECK (btrim(descripcion) <> ''),
    estado VARCHAR(10) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR','EMITIDO')),
    numero BIGINT CHECK (numero > 0),
    mano_obra NUMERIC(18,2) NOT NULL CHECK (mano_obra >= 0),
    porcentaje_iva NUMERIC(5,2) NOT NULL CHECK (porcentaje_iva BETWEEN 0 AND 100),
    total_repuestos NUMERIC(18,2) NOT NULL CHECK (total_repuestos >= 0),
    subtotal NUMERIC(18,2) NOT NULL CHECK (subtotal = total_repuestos + mano_obra),
    monto_iva NUMERIC(18,2) NOT NULL CHECK (monto_iva = round(subtotal * porcentaje_iva / 100,2)),
    total NUMERIC(18,2) NOT NULL CHECK (total = subtotal + monto_iva AND total > 0),
    emisor_nombre TEXT NOT NULL, emisor_documento TEXT NOT NULL, emisor_calle TEXT,
    emisor_altura INTEGER, emisor_iva TEXT NOT NULL, emisor_telefono TEXT,
    receptor_nombre TEXT NOT NULL, receptor_documento TEXT NOT NULL, receptor_calle TEXT,
    receptor_altura INTEGER, receptor_iva TEXT NOT NULL, receptor_telefono TEXT,
    patente VARCHAR(9) NOT NULL, marca TEXT NOT NULL, modelo TEXT NOT NULL, version TEXT, anio INTEGER NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por BIGINT NOT NULL,
    emitido_en TIMESTAMPTZ,
    emitido_por BIGINT,
    fecha_modif TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_modificacion_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id,orden_id) REFERENCES octano.orden_trabajo(taller_id,id),
    FOREIGN KEY (taller_id,cliente_id) REFERENCES octano.cliente(taller_id,id),
    FOREIGN KEY (taller_id,vehiculo_id) REFERENCES octano.vehiculo(taller_id,id),
    FOREIGN KEY (taller_id,creado_por) REFERENCES octano.usuario(taller_id,id),
    FOREIGN KEY (taller_id,emitido_por) REFERENCES octano.usuario(taller_id,id),
    FOREIGN KEY (taller_id,usuario_modificacion_id) REFERENCES octano.usuario(taller_id,id),
    UNIQUE (taller_id,id), UNIQUE (taller_id,orden_id), UNIQUE (taller_id,numero),
    CHECK ((estado='BORRADOR' AND numero IS NULL AND emitido_en IS NULL AND emitido_por IS NULL)
        OR (estado='EMITIDO' AND numero IS NOT NULL AND emitido_en IS NOT NULL AND emitido_por IS NOT NULL))
);
CREATE INDEX comprobante_listado_idx ON octano.comprobante(taller_id,fecha,id);

CREATE TABLE octano.detalle_comprobante (
    taller_id BIGINT NOT NULL,
    comprobante_id BIGINT NOT NULL,
    renglon INTEGER NOT NULL CHECK (renglon BETWEEN 1 AND 200),
    repuesto_id BIGINT,
    codigo_oem VARCHAR(80),
    descripcion VARCHAR(250) NOT NULL CHECK (btrim(descripcion) <> ''),
    cantidad NUMERIC(15,3) NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(18,2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal NUMERIC(18,2) NOT NULL CHECK (subtotal = round(cantidad * precio_unitario,2)),
    PRIMARY KEY (taller_id,comprobante_id,renglon),
    FOREIGN KEY (taller_id,comprobante_id) REFERENCES octano.comprobante(taller_id,id),
    FOREIGN KEY (taller_id,repuesto_id) REFERENCES octano.repuesto(taller_id,id)
);

CREATE TABLE octano.cobro (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL,
    comprobante_id BIGINT NOT NULL,
    fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    importe NUMERIC(18,2) NOT NULL CHECK (importe > 0),
    medio_pago VARCHAR(20) NOT NULL CHECK (medio_pago IN ('EFECTIVO','TRANSFERENCIA','TARJETA_DEBITO','TARJETA_CREDITO','MERCADO_PAGO')),
    observaciones VARCHAR(1000) NOT NULL DEFAULT '',
    usuario_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id,comprobante_id) REFERENCES octano.comprobante(taller_id,id),
    FOREIGN KEY (taller_id,usuario_id) REFERENCES octano.usuario(taller_id,id),
    UNIQUE (taller_id,comprobante_id)
);
CREATE INDEX cobro_fecha_idx ON octano.cobro(taller_id,fecha,id);
