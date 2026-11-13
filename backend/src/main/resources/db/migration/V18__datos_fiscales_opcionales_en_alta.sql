ALTER TABLE octano.taller
    ALTER COLUMN cuit DROP NOT NULL,
    ALTER COLUMN inicio_actividades DROP NOT NULL,
    ALTER COLUMN condicion_iva_id DROP NOT NULL;

ALTER TABLE octano.comprobante
    ALTER COLUMN emisor_documento DROP NOT NULL,
    ALTER COLUMN emisor_iva DROP NOT NULL;
