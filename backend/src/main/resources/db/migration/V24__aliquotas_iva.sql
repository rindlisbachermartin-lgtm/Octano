CREATE TABLE octano.aliquota_iva (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    porcentaje NUMERIC(5,2) NOT NULL UNIQUE CHECK (porcentaje >= 0 AND porcentaje <= 100)
);
INSERT INTO octano.aliquota_iva (porcentaje) VALUES (0), (10.5), (21);
