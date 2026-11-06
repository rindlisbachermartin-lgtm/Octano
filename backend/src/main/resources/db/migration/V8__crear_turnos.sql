CREATE TABLE octano.turno (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    cliente_id BIGINT NOT NULL,
    vehiculo_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME(0) NOT NULL,
    hora_fin TIME(0) NOT NULL,
    motivo_visita VARCHAR(500) NOT NULL CHECK (btrim(motivo_visita) <> ''),
    estado VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADO' CHECK (estado IN ('PROGRAMADO', 'CONFIRMADO', 'CANCELADO', 'NO_ASISTIO')),
    superposicion_autorizada BOOLEAN NOT NULL DEFAULT FALSE,
    autorizada_por BIGINT,
    autorizada_fecha TIMESTAMPTZ,
    fecha_modif TIMESTAMPTZ NOT NULL,
    usuario_modificacion_id BIGINT NOT NULL,
    CHECK (hora_fin > hora_inicio),
    CHECK ((superposicion_autorizada AND autorizada_por IS NOT NULL AND autorizada_fecha IS NOT NULL)
        OR (NOT superposicion_autorizada AND autorizada_por IS NULL AND autorizada_fecha IS NULL)),
    FOREIGN KEY (taller_id, cliente_id) REFERENCES octano.cliente(taller_id, id),
    FOREIGN KEY (taller_id, vehiculo_id) REFERENCES octano.vehiculo(taller_id, id),
    FOREIGN KEY (taller_id, usuario_modificacion_id) REFERENCES octano.usuario(taller_id, id),
    FOREIGN KEY (taller_id, autorizada_por) REFERENCES octano.usuario(taller_id, id),
    UNIQUE (taller_id, id)
);
CREATE INDEX turno_agenda_idx ON octano.turno(taller_id, fecha, hora_inicio);
CREATE UNIQUE INDEX turno_vehiculo_dia_unico ON octano.turno(taller_id, vehiculo_id, fecha)
    WHERE estado IN ('PROGRAMADO', 'CONFIRMADO');
