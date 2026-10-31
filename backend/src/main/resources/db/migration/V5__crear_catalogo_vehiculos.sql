CREATE TABLE octano.marca (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL CHECK (btrim(nombre) <> '')
);
CREATE UNIQUE INDEX marca_nombre_unico ON octano.marca(lower(nombre));
CREATE TABLE octano.modelo (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    marca_id BIGINT NOT NULL REFERENCES octano.marca(id),
    nombre VARCHAR(80) NOT NULL CHECK (btrim(nombre) <> '')
);
CREATE UNIQUE INDEX modelo_nombre_unico ON octano.modelo(marca_id, lower(nombre));
CREATE TABLE octano.version_vehiculo (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    modelo_id BIGINT NOT NULL REFERENCES octano.modelo(id),
    nombre VARCHAR(80) NOT NULL CHECK (btrim(nombre) <> ''),
    UNIQUE (modelo_id, id)
);
CREATE UNIQUE INDEX version_nombre_unico ON octano.version_vehiculo(modelo_id, lower(nombre));

