CREATE TABLE octano.tipo_usuario (
    id SMALLINT PRIMARY KEY,
    nombre VARCHAR(20) NOT NULL UNIQUE
);
INSERT INTO octano.tipo_usuario VALUES (1, 'ADMIN'), (2, 'RECEPCION'), (3, 'MECANICO');

CREATE TABLE octano.mecanico (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    nombre VARCHAR(90) NOT NULL CHECK (btrim(nombre) <> ''),
    apellido VARCHAR(90) NOT NULL CHECK (btrim(apellido) <> ''),
    telefono VARCHAR(30) NOT NULL,
    calle VARCHAR(150),
    cuit VARCHAR(11),
    fecha_inicio DATE NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (taller_id, id)
);

CREATE TABLE octano.usuario (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    taller_id BIGINT NOT NULL REFERENCES octano.taller(id),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(btrim(email))),
    password_hash VARCHAR(255) NOT NULL,
    token_version INTEGER NOT NULL DEFAULT 0,
    tipo_usuario_id SMALLINT NOT NULL REFERENCES octano.tipo_usuario(id),
    mecanico_id BIGINT UNIQUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (taller_id, mecanico_id) REFERENCES octano.mecanico(taller_id, id),
    CHECK ((tipo_usuario_id = 3 AND mecanico_id IS NOT NULL) OR (tipo_usuario_id <> 3 AND mecanico_id IS NULL)),
    UNIQUE (taller_id, id)
);
CREATE INDEX usuario_taller_idx ON octano.usuario(taller_id);
