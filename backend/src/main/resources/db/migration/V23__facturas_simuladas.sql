ALTER TABLE octano.comprobante
    ADD COLUMN tipo_simulado CHAR(1) NOT NULL DEFAULT 'X' CHECK (tipo_simulado IN ('X','A','B','C')),
    ADD COLUMN cae_simulado CHAR(14),
    ADD COLUMN punto_venta INTEGER NOT NULL DEFAULT 3 CHECK (punto_venta BETWEEN 1 AND 99999);

ALTER TABLE octano.comprobante
    ADD CONSTRAINT comprobante_cae_simulado_valido CHECK (
        (tipo_simulado = 'X' AND cae_simulado IS NULL)
        OR (tipo_simulado IN ('A','B','C') AND cae_simulado ~ '^[0-9]{14}$')
    );
