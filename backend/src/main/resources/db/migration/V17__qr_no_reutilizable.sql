ALTER TABLE octano.codigo_qr ADD COLUMN inutilizado_en TIMESTAMPTZ;
ALTER TABLE octano.codigo_qr ADD CONSTRAINT qr_inutilizado_sin_vehiculo
    CHECK (inutilizado_en IS NULL OR vehiculo_id IS NULL);

-- Un QR usado solo puede conservar su vehículo o retirarse definitivamente.
CREATE FUNCTION octano.validar_uso_qr() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.inutilizado_en IS NOT NULL AND NEW.inutilizado_en IS DISTINCT FROM OLD.inutilizado_en THEN
        RAISE EXCEPTION 'Un QR inutilizado no puede reactivarse' USING ERRCODE = '23514';
    END IF;
    IF OLD.vehiculo_id IS NOT NULL AND NEW.vehiculo_id IS DISTINCT FROM OLD.vehiculo_id
       AND (NEW.vehiculo_id IS NOT NULL OR NEW.inutilizado_en IS NULL) THEN
        RAISE EXCEPTION 'Un QR usado no puede reutilizarse' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER qr_uso_unico BEFORE UPDATE ON octano.codigo_qr
    FOR EACH ROW EXECUTE FUNCTION octano.validar_uso_qr();
