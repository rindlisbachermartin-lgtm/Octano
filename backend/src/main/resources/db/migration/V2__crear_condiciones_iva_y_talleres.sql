CREATE TABLE octano.condicion_iva (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    CONSTRAINT condicion_iva_nombre_valido CHECK (btrim(nombre) <> '')
);

-- Mismas opciones que utiliza el frontend. Son identificadores internos del sistema.
INSERT INTO octano.condicion_iva (nombre) VALUES
    ('Consumidor Final'),
    ('IVA Responsable Inscripto'),
    ('Responsable Monotributo'),
    ('IVA Sujeto Exento'),
    ('IVA No Alcanzado'),
    ('Monotributista Social'),
    ('Monotributo Trabajador Independiente Promovido'),
    ('Sujeto No Categorizado');

CREATE TABLE octano.taller (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    calle VARCHAR(150) NOT NULL,
    altura INTEGER NOT NULL,
    telefono VARCHAR(30) NOT NULL,
    cuit VARCHAR(11) NOT NULL,
    ingresos_brutos VARCHAR(40),
    inicio_actividades DATE NOT NULL,
    condicion_iva_id BIGINT NOT NULL REFERENCES octano.condicion_iva(id),
    CONSTRAINT taller_nombre_valido CHECK (btrim(nombre) <> ''),
    CONSTRAINT taller_calle_valida CHECK (btrim(calle) <> ''),
    CONSTRAINT taller_altura_valida CHECK (altura >= 0),
    CONSTRAINT taller_telefono_valido CHECK (btrim(telefono) <> ''),
    CONSTRAINT taller_cuit_formato CHECK (cuit ~ '^[0-9]{11}$')
);

CREATE INDEX taller_condicion_iva_idx ON octano.taller(condicion_iva_id);
