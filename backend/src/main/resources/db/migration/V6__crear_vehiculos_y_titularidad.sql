CREATE TABLE octano.vehiculo (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    cliente_id BIGINT NOT NULL,
    modelo_id BIGINT NOT NULL REFERENCES octano.modelo(id),
    version_id BIGINT,
    patente VARCHAR(9) NOT NULL CHECK (patente ~ '^([A-Z]{3}-[0-9]{3}|[A-Z]{2}-[0-9]{3}-[A-Z]{2})$'),
    anio INTEGER NOT NULL CHECK (anio >= 1900),
    kilometraje INTEGER NOT NULL CHECK (kilometraje BETWEEN 0 AND 9999999),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_modif TIMESTAMPTZ NOT NULL,
    usuario_modificacion_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id, cliente_id) REFERENCES octano.cliente(taller_id, id),
    FOREIGN KEY (taller_id, usuario_modificacion_id) REFERENCES octano.usuario(taller_id, id),
    FOREIGN KEY (modelo_id, version_id) REFERENCES octano.version_vehiculo(modelo_id, id),
    UNIQUE (taller_id, patente), UNIQUE (taller_id, id)
);
CREATE INDEX vehiculo_cliente_idx ON octano.vehiculo(taller_id, cliente_id);

CREATE TABLE octano.historial_titularidad (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL,
    vehiculo_id BIGINT NOT NULL,
    cliente_anterior_id BIGINT,
    cliente_nuevo_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    fecha TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (taller_id, vehiculo_id) REFERENCES octano.vehiculo(taller_id, id),
    FOREIGN KEY (taller_id, cliente_anterior_id) REFERENCES octano.cliente(taller_id, id),
    FOREIGN KEY (taller_id, cliente_nuevo_id) REFERENCES octano.cliente(taller_id, id),
    FOREIGN KEY (taller_id, usuario_id) REFERENCES octano.usuario(taller_id, id)
);
CREATE INDEX titularidad_vehiculo_idx ON octano.historial_titularidad(taller_id, vehiculo_id);
