CREATE TABLE octano.cliente (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    nombre VARCHAR(90) NOT NULL CHECK (length(btrim(nombre)) >= 2),
    documento VARCHAR(11) NOT NULL CHECK (documento ~ '^([0-9]{7,8}|[0-9]{11})$'),
    telefono VARCHAR(30) NOT NULL,
    email VARCHAR(254),
    calle VARCHAR(150),
    altura INTEGER CHECK (altura >= 0),
    condicion_iva_id BIGINT NOT NULL REFERENCES octano.condicion_iva(id),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_modif TIMESTAMPTZ NOT NULL,
    usuario_modificacion_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id, usuario_modificacion_id) REFERENCES octano.usuario(taller_id, id),
    UNIQUE (taller_id, documento),
    UNIQUE (taller_id, id)
);
