CREATE TABLE octano.codigo_qr (
    codigo VARCHAR(40) PRIMARY KEY CHECK (codigo ~ '^[A-Z0-9-]{3,40}$'),
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    vehiculo_id BIGINT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    asignado_en TIMESTAMPTZ,
    impreso_en TIMESTAMPTZ,
    impreso_por BIGINT,
    fecha_modif TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_modificacion_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id,vehiculo_id) REFERENCES octano.vehiculo(taller_id,id),
    FOREIGN KEY (taller_id,impreso_por) REFERENCES octano.usuario(taller_id,id),
    FOREIGN KEY (taller_id,usuario_modificacion_id) REFERENCES octano.usuario(taller_id,id),
    UNIQUE (taller_id,vehiculo_id),
    CHECK ((vehiculo_id IS NULL) = (asignado_en IS NULL)),
    CHECK ((impreso_en IS NULL) = (impreso_por IS NULL))
);
CREATE INDEX codigo_qr_listado_idx ON octano.codigo_qr(taller_id,creado_en,codigo);
