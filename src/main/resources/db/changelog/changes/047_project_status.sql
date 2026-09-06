--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Estado del proyecto y control de cierre.
--
-- Faltaba el paso previo a la ejecución. Un proyecto que nace de una
-- cotización aprobada ya trae respaldo comercial y puede arrancar; uno
-- creado a mano no trae ninguno, y hasta ahora podía empezar a consumir
-- inventario y acumular costos sin que nadie lo aprobara.
--
-- No se agrega un estado 'approved' aparte: 'open' YA significa "aprobado y
-- en ejecución" —es el estado en el que ProjectService admite cargos y
-- consumos—. Un tercer nombre para lo mismo sería un estado más que vigilar
-- sin ganar nada. El flujo queda:
--
--     draft ──aprobar──> open ──> closed
--       └──────────────────────> cancelled
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:047-project-draft-status
--comment: Borrador: creado pero sin aprobar. Lo existente sigue en 'open'.
ALTER TABLE projects DROP CONSTRAINT ck_projects_status;
ALTER TABLE projects ADD CONSTRAINT ck_projects_status
    CHECK (status IN ('draft', 'open', 'closed', 'cancelled'));
COMMENT ON COLUMN projects.status IS
    'draft borrador sin aprobar | open aprobado y en ejecución, admite cargos | '
    'closed liquidado | cancelled anulado.';
--rollback ALTER TABLE projects DROP CONSTRAINT ck_projects_status; ALTER TABLE projects ADD CONSTRAINT ck_projects_status CHECK (status IN ('open','closed','cancelled'));

--changeset erp_maya:047-project-close-note
--comment: Por qué se cerró un proyecto con menos facturado que ejecutado.
-- Cerrar así es legítimo —una garantía que se absorbe, un descuento pactado al
-- final— pero es plata que se gastó y no se le cobró a nadie. Se permite y se
-- exige decir por qué: una justificación que no se guarda no sirve de nada.
ALTER TABLE projects ADD COLUMN close_note VARCHAR(300);
--rollback ALTER TABLE projects DROP COLUMN close_note;
