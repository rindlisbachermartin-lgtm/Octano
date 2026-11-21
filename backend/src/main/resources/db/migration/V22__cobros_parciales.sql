-- Un comprobante puede cancelarse en varias cuotas: se levanta la restricción de un único cobro.
DO $$
DECLARE
    restriccion text;
BEGIN
    SELECT con.conname INTO restriccion
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace ns ON ns.oid = rel.relnamespace
    WHERE ns.nspname = 'octano' AND rel.relname = 'cobro' AND con.contype = 'u'
      AND array_length(con.conkey, 1) = 2
      AND con.conkey @> ARRAY[
          (SELECT attnum FROM pg_attribute WHERE attrelid = con.conrelid AND attname = 'taller_id'),
          (SELECT attnum FROM pg_attribute WHERE attrelid = con.conrelid AND attname = 'comprobante_id')
      ]::smallint[];
    IF restriccion IS NOT NULL THEN
        EXECUTE format('ALTER TABLE octano.cobro DROP CONSTRAINT %I', restriccion);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS cobro_comprobante_idx ON octano.cobro(taller_id, comprobante_id, id);
