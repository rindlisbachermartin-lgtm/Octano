-- Cada repuesto de inventario tiene un único renglón por OT.
-- Los conceptos manuales, sin repuesto_id, no representan un mismo artículo de inventario.
CREATE UNIQUE INDEX orden_repuesto_unico ON octano.repuesto_orden(taller_id,orden_id,repuesto_id)
    WHERE repuesto_id IS NOT NULL;
