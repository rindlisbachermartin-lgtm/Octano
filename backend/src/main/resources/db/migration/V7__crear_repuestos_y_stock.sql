CREATE TABLE octano.repuesto (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    codigo_oem VARCHAR(80) NOT NULL CHECK (btrim(codigo_oem) <> ''),
    descripcion VARCHAR(250) NOT NULL CHECK (btrim(descripcion) <> ''),
    marca VARCHAR(80) NOT NULL CHECK (btrim(marca) <> ''),
    precio_costo NUMERIC(18,2) NOT NULL CHECK (precio_costo >= 0),
    margen NUMERIC(6,2) NOT NULL CHECK (margen >= 0),
    precio_publico NUMERIC(18,2) NOT NULL CHECK (precio_publico >= 0),
    stock NUMERIC(15,3) NOT NULL DEFAULT 0 CHECK (stock >= 0),
    stock_minimo NUMERIC(15,3) NOT NULL CHECK (stock_minimo >= 0),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_modif TIMESTAMPTZ NOT NULL,
    usuario_modificacion_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id, usuario_modificacion_id) REFERENCES octano.usuario(taller_id, id),
    UNIQUE (taller_id, id)
);
CREATE UNIQUE INDEX repuesto_codigo_unico ON octano.repuesto(taller_id, lower(codigo_oem));

CREATE TABLE octano.compatibilidad_repuesto (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL,
    repuesto_id BIGINT NOT NULL,
    modelo_id BIGINT NOT NULL REFERENCES octano.modelo(id),
    version_id BIGINT,
    FOREIGN KEY (taller_id, repuesto_id) REFERENCES octano.repuesto(taller_id, id),
    FOREIGN KEY (modelo_id, version_id) REFERENCES octano.version_vehiculo(modelo_id, id),
    UNIQUE NULLS NOT DISTINCT (taller_id, repuesto_id, modelo_id, version_id)
);

CREATE TABLE octano.movimiento_stock (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL,
    repuesto_id BIGINT NOT NULL,
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE')),
    cantidad NUMERIC(15,3) NOT NULL CHECK (cantidad >= 0),
    variacion NUMERIC(15,3) NOT NULL CHECK (variacion <> 0),
    stock_anterior NUMERIC(15,3) NOT NULL CHECK (stock_anterior >= 0),
    stock_posterior NUMERIC(15,3) NOT NULL CHECK (stock_posterior >= 0),
    motivo VARCHAR(250) NOT NULL CHECK (btrim(motivo) <> ''),
    usuario_id BIGINT NOT NULL,
    fecha TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (taller_id, repuesto_id) REFERENCES octano.repuesto(taller_id, id),
    FOREIGN KEY (taller_id, usuario_id) REFERENCES octano.usuario(taller_id, id),
    CHECK (stock_posterior = stock_anterior + variacion),
    CHECK ((tipo = 'ENTRADA' AND cantidad > 0 AND variacion = cantidad)
        OR (tipo = 'SALIDA' AND cantidad > 0 AND variacion = -cantidad)
        OR (tipo = 'AJUSTE' AND stock_posterior = cantidad))
);
CREATE INDEX movimiento_stock_repuesto_idx ON octano.movimiento_stock(taller_id, repuesto_id, id);
