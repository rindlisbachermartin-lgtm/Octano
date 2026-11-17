ALTER TABLE octano.turno DROP CONSTRAINT turno_estado_check;
ALTER TABLE octano.turno ADD CONSTRAINT turno_estado_check
    CHECK (estado IN ('PROGRAMADO','CONFIRMADO','EN_TALLER','FINALIZADO','CANCELADO','NO_ASISTIO'));

CREATE TABLE octano.orden_trabajo (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    cliente_id BIGINT NOT NULL,
    vehiculo_id BIGINT NOT NULL,
    turno_id BIGINT,
    presupuesto_id BIGINT,
    mecanico_id BIGINT,
    descripcion VARCHAR(2000) NOT NULL CHECK (btrim(descripcion) <> ''),
    tipos_trabajo TEXT[] NOT NULL CHECK (cardinality(tipos_trabajo) BETWEEN 1 AND 3)
        CHECK (tipos_trabajo <@ ARRAY['SERVICE', 'DISTRIBUCION', 'OTRO']),
    indicaciones VARCHAR(4000) NOT NULL DEFAULT '',
    diagnostico VARCHAR(4000) NOT NULL DEFAULT '',
    observaciones_mecanico VARCHAR(4000) NOT NULL DEFAULT '',
    kilometraje INTEGER CHECK (kilometraje BETWEEN 0 AND 9999999),
    aceite VARCHAR(120) NOT NULL DEFAULT '',
    filtros TEXT[] NOT NULL DEFAULT '{}',
    mano_obra NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (mano_obra >= 0),
    porcentaje_iva NUMERIC(5,2) NOT NULL DEFAULT 21 CHECK (porcentaje_iva BETWEEN 0 AND 100),
    estado VARCHAR(25) NOT NULL CHECK (estado IN ('PENDIENTE_INGRESO','EN_ESPERA','EN_PROCESO','FINALIZADO','CANCELADO')),
    motivo_cancelacion VARCHAR(500),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ingreso_fecha TIMESTAMPTZ,
    inicio_fecha TIMESTAMPTZ,
    fin_fecha TIMESTAMPTZ,
    estado_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado_usuario_id BIGINT NOT NULL,
    fecha_modif TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_modificacion_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id, cliente_id) REFERENCES octano.cliente(taller_id, id),
    FOREIGN KEY (taller_id, vehiculo_id) REFERENCES octano.vehiculo(taller_id, id),
    FOREIGN KEY (taller_id, turno_id) REFERENCES octano.turno(taller_id, id),
    FOREIGN KEY (taller_id, presupuesto_id) REFERENCES octano.presupuesto(taller_id, id),
    FOREIGN KEY (taller_id, mecanico_id) REFERENCES octano.mecanico(taller_id, id),
    FOREIGN KEY (taller_id, estado_usuario_id) REFERENCES octano.usuario(taller_id, id),
    FOREIGN KEY (taller_id, usuario_modificacion_id) REFERENCES octano.usuario(taller_id, id),
    UNIQUE (taller_id, id), UNIQUE (taller_id, turno_id),
    CHECK (estado <> 'EN_PROCESO' OR (mecanico_id IS NOT NULL AND inicio_fecha IS NOT NULL)),
    CHECK (estado <> 'FINALIZADO' OR (fin_fecha IS NOT NULL AND kilometraje IS NOT NULL)),
    CHECK (estado <> 'CANCELADO' OR btrim(motivo_cancelacion) <> '')
);
CREATE UNIQUE INDEX orden_presupuesto_unico ON octano.orden_trabajo(taller_id, presupuesto_id)
    WHERE presupuesto_id IS NOT NULL AND estado <> 'CANCELADO';
CREATE INDEX orden_listado_idx ON octano.orden_trabajo(taller_id, estado, id);

CREATE TABLE octano.tarea_orden (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL,
    orden_id BIGINT NOT NULL,
    descripcion VARCHAR(500) NOT NULL CHECK (btrim(descripcion) <> ''),
    completada BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (taller_id, orden_id) REFERENCES octano.orden_trabajo(taller_id, id)
);
CREATE INDEX tarea_orden_idx ON octano.tarea_orden(taller_id, orden_id);

CREATE TABLE octano.repuesto_orden (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL,
    orden_id BIGINT NOT NULL,
    repuesto_id BIGINT,
    codigo_oem VARCHAR(80),
    descripcion VARCHAR(250) NOT NULL CHECK (btrim(descripcion) <> ''),
    cantidad NUMERIC(15,3) NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(18,2) NOT NULL CHECK (precio_unitario >= 0),
    subtotal NUMERIC(18,2) NOT NULL CHECK (subtotal = round(cantidad * precio_unitario, 2)),
    presupuestado BOOLEAN NOT NULL DEFAULT FALSE,
    consumido BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (taller_id, orden_id) REFERENCES octano.orden_trabajo(taller_id, id),
    FOREIGN KEY (taller_id, repuesto_id) REFERENCES octano.repuesto(taller_id, id)
);
CREATE INDEX repuesto_orden_idx ON octano.repuesto_orden(taller_id, orden_id);

-- Las reservas existentes también aparecen como órdenes; todavía no consumen inventario.
INSERT INTO octano.orden_trabajo(taller_id, cliente_id, vehiculo_id, turno_id, presupuesto_id,
    descripcion, tipos_trabajo, mano_obra, porcentaje_iva, estado, ingreso_fecha, motivo_cancelacion,
    estado_usuario_id, usuario_modificacion_id)
SELECT t.taller_id,t.cliente_id,t.vehiculo_id,t.id,t.presupuesto_id,t.motivo_visita,
    COALESCE(p.tipos_trabajo,ARRAY['OTRO']),COALESCE(p.mano_obra,0),COALESCE(p.porcentaje_iva,21),
    CASE WHEN t.estado='EN_TALLER' THEN 'EN_ESPERA' WHEN t.estado IN ('CANCELADO','NO_ASISTIO') THEN 'CANCELADO' ELSE 'PENDIENTE_INGRESO' END,
    CASE WHEN t.estado='EN_TALLER' THEN t.fecha_modif ELSE NULL END,
    CASE WHEN t.estado IN ('CANCELADO','NO_ASISTIO') THEN 'Turno cancelado o sin asistencia.' ELSE NULL END,
    t.usuario_modificacion_id,t.usuario_modificacion_id
FROM octano.turno t LEFT JOIN octano.presupuesto p ON p.id=t.presupuesto_id AND p.taller_id=t.taller_id;
INSERT INTO octano.repuesto_orden(taller_id,orden_id,repuesto_id,codigo_oem,descripcion,cantidad,precio_unitario,subtotal,presupuestado)
SELECT o.taller_id,o.id,d.repuesto_id,d.codigo_oem,d.descripcion,d.cantidad,d.precio_unitario,d.subtotal,TRUE
FROM octano.orden_trabajo o JOIN octano.detalle_presupuesto d ON d.taller_id=o.taller_id AND d.presupuesto_id=o.presupuesto_id;
INSERT INTO octano.tarea_orden(taller_id,orden_id,descripcion)
SELECT taller_id,id,left(descripcion,500) FROM octano.orden_trabajo;
