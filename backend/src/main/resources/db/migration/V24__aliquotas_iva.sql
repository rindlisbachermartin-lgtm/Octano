CREATE TABLE octano.aliquota_iva (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    porcentaje NUMERIC(5,2) NOT NULL CHECK (porcentaje >= 0 AND porcentaje <= 100)
);
CREATE UNIQUE INDEX aliquota_iva_porcentaje_unico ON octano.aliquota_iva (TRIM(TO_CHAR(porcentaje, 'FM999990.99')));
INSERT INTO octano.aliquota_iva (porcentaje) VALUES (0), (10.5), (21);
