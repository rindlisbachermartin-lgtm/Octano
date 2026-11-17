ALTER TABLE octano.usuario ADD COLUMN dueno BOOLEAN NOT NULL DEFAULT FALSE;

-- El alta del taller crea su primer administrador en la misma transacción.
UPDATE octano.usuario u
SET dueno = TRUE
FROM (
    SELECT taller_id, MIN(id) AS id
    FROM octano.usuario
    WHERE tipo_usuario_id = 1
    GROUP BY taller_id
) fundador
WHERE u.taller_id = fundador.taller_id AND u.id = fundador.id;

CREATE UNIQUE INDEX usuario_dueno_por_taller_idx ON octano.usuario (taller_id) WHERE dueno;
ALTER TABLE octano.usuario ADD CONSTRAINT usuario_dueno_es_admin CHECK (NOT dueno OR tipo_usuario_id = 1);
