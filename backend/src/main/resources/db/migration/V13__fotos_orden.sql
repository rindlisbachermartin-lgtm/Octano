CREATE TABLE octano.foto_orden (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL,
    orden_id BIGINT NOT NULL,
    sector VARCHAR(80) NOT NULL CHECK (btrim(sector) <> ''),
    tipo_contenido VARCHAR(10) NOT NULL CHECK (tipo_contenido IN ('image/jpeg','image/png')),
    datos BYTEA NOT NULL CHECK (octet_length(datos) BETWEEN 1 AND 1048576),
    fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_id BIGINT NOT NULL,
    FOREIGN KEY (taller_id, orden_id) REFERENCES octano.orden_trabajo(taller_id, id),
    FOREIGN KEY (taller_id, usuario_id) REFERENCES octano.usuario(taller_id, id)
);
CREATE INDEX foto_orden_idx ON octano.foto_orden(taller_id, orden_id);
