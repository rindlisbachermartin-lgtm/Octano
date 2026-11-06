ALTER TABLE octano.turno DROP CONSTRAINT turno_estado_check;
ALTER TABLE octano.turno ADD CONSTRAINT turno_estado_check
    CHECK (estado IN ('PROGRAMADO', 'CONFIRMADO', 'EN_TALLER', 'CANCELADO', 'NO_ASISTIO'));

DROP INDEX octano.turno_vehiculo_dia_unico;
CREATE UNIQUE INDEX turno_vehiculo_dia_unico ON octano.turno(taller_id, vehiculo_id, fecha)
    WHERE estado IN ('PROGRAMADO', 'CONFIRMADO', 'EN_TALLER');

DROP INDEX octano.presupuesto_turno_activo_unico;
CREATE UNIQUE INDEX presupuesto_turno_activo_unico ON octano.turno(taller_id, presupuesto_id)
    WHERE presupuesto_id IS NOT NULL AND estado IN ('PROGRAMADO', 'CONFIRMADO', 'EN_TALLER');
