--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Stackline Salud · Agenda.
--
-- Profesionales: usuarios de Stackline con perfil de salud (especialidad,
-- colegiado, color en la agenda). No hay un segundo login: el profesional
-- entra con su usuario de siempre.
--
-- Recursos: consultorios, sillones o equipos que se reservan junto con la
-- cita. Tipos de cita: duración y precio de referencia.
--
-- Horarios: semanales por profesional (día ISO 1 = lunes … 7 = domingo).
-- Bloqueos: vacaciones, congresos o un consultorio en mantenimiento.
--
-- No solape: dos citas activas no pueden cruzarse para el mismo
-- profesional ni para el mismo recurso. Lo garantiza la base con una
-- restricción EXCLUDE, no la aplicación: dos recepcionistas reservando el
-- mismo horario al mismo tiempo no pueden ganar las dos. Se usa
-- int8range(id, id) en vez de `id WITH =` para no depender de la extensión
-- btree_gist (los tipos de rango ya traen soporte GiST).
--
-- Una cita cancelada o en la que el paciente no se presentó libera el
-- horario: queda fuera de la restricción.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:090-salud-agenda
CREATE TABLE salud_professionals (
    user_id         BIGINT        PRIMARY KEY REFERENCES users (id),
    company_id      BIGINT        NOT NULL REFERENCES companies (id),
    specialty       VARCHAR(80),
    license_number  VARCHAR(40),
    color           VARCHAR(9),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_salud_professionals_company ON salud_professionals (company_id, active);

CREATE TABLE salud_resources (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT        NOT NULL REFERENCES companies (id),
    branch_id   BIGINT        REFERENCES branches (id),
    name        VARCHAR(80)   NOT NULL,
    kind        VARCHAR(12)   NOT NULL DEFAULT 'consultorio',
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_salud_resources_name UNIQUE (company_id, name),
    CONSTRAINT ck_salud_resources_kind CHECK (kind IN ('consultorio', 'sillon', 'equipo', 'sala', 'otro'))
);

CREATE TABLE salud_appointment_types (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT         NOT NULL REFERENCES companies (id),
    name               VARCHAR(80)    NOT NULL,
    duration_minutes   INTEGER        NOT NULL,
    price              NUMERIC(12, 2),
    color              VARCHAR(9),
    requires_resource  BOOLEAN        NOT NULL DEFAULT FALSE,
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_salud_types_name UNIQUE (company_id, name),
    CONSTRAINT ck_salud_types_duration CHECK (duration_minutes BETWEEN 5 AND 480),
    CONSTRAINT ck_salud_types_price CHECK (price IS NULL OR price >= 0)
);

CREATE TABLE salud_schedules (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT     NOT NULL REFERENCES companies (id),
    professional_id  BIGINT     NOT NULL REFERENCES salud_professionals (user_id) ON DELETE CASCADE,
    weekday          SMALLINT   NOT NULL,
    start_time       TIME       NOT NULL,
    end_time         TIME       NOT NULL,
    resource_id      BIGINT     REFERENCES salud_resources (id),
    CONSTRAINT ck_salud_schedules_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_salud_schedules_range CHECK (end_time > start_time)
);
CREATE INDEX ix_salud_schedules_professional ON salud_schedules (professional_id, weekday);

CREATE TABLE salud_blocks (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT         NOT NULL REFERENCES companies (id),
    professional_id  BIGINT         REFERENCES salud_professionals (user_id),
    resource_id      BIGINT         REFERENCES salud_resources (id),
    starts_at        TIMESTAMPTZ    NOT NULL,
    ends_at          TIMESTAMPTZ    NOT NULL,
    reason           VARCHAR(200),
    created_by       BIGINT         REFERENCES users (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_salud_blocks_target CHECK (professional_id IS NOT NULL OR resource_id IS NOT NULL),
    CONSTRAINT ck_salud_blocks_range CHECK (ends_at > starts_at)
);
CREATE INDEX ix_salud_blocks_range ON salud_blocks (company_id, starts_at, ends_at);

CREATE TABLE salud_appointments (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT         NOT NULL REFERENCES companies (id),
    patient_id       BIGINT         NOT NULL REFERENCES salud_patients (id),
    professional_id  BIGINT         NOT NULL REFERENCES salud_professionals (user_id),
    resource_id      BIGINT         REFERENCES salud_resources (id),
    type_id          BIGINT         NOT NULL REFERENCES salud_appointment_types (id),
    starts_at        TIMESTAMPTZ    NOT NULL,
    ends_at          TIMESTAMPTZ    NOT NULL,
    status           VARCHAR(12)    NOT NULL DEFAULT 'solicitada',
    source           VARCHAR(12)    NOT NULL DEFAULT 'recepcion',
    reason           VARCHAR(300),
    notes            VARCHAR(1000),
    cancel_reason    VARCHAR(300),
    created_by       BIGINT         REFERENCES users (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    status_changed_by BIGINT        REFERENCES users (id),
    confirmed_at     TIMESTAMPTZ,
    arrived_at       TIMESTAMPTZ,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    CONSTRAINT ck_salud_appt_range CHECK (ends_at > starts_at),
    CONSTRAINT ck_salud_appt_status CHECK (status IN ('solicitada', 'confirmada', 'en_sala', 'en_atencion',
                                                      'completada', 'cancelada', 'no_asistio')),
    CONSTRAINT ck_salud_appt_source CHECK (source IN ('recepcion', 'tino', 'portal')),
    CONSTRAINT ck_salud_appt_cancel CHECK (status <> 'cancelada' OR cancel_reason IS NOT NULL),
    CONSTRAINT ex_salud_appt_professional EXCLUDE USING gist (
        int8range(professional_id, professional_id, '[]') WITH &&,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status NOT IN ('cancelada', 'no_asistio')),
    CONSTRAINT ex_salud_appt_resource EXCLUDE USING gist (
        int8range(resource_id, resource_id, '[]') WITH &&,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (resource_id IS NOT NULL AND status NOT IN ('cancelada', 'no_asistio'))
);
CREATE INDEX ix_salud_appt_company_start ON salud_appointments (company_id, starts_at);
CREATE INDEX ix_salud_appt_patient ON salud_appointments (patient_id, starts_at DESC);

-- La agenda con los nombres que la pantalla necesita, de solo lectura.
CREATE VIEW salud_v_appointments AS
SELECT a.id, a.company_id, a.patient_id, a.professional_id, a.resource_id, a.type_id,
       a.starts_at, a.ends_at, a.status, a.source, a.reason, a.notes, a.cancel_reason,
       a.created_at, a.confirmed_at, a.arrived_at, a.started_at, a.completed_at, a.cancelled_at,
       c.name AS patient_name, c.phone AS patient_phone, p.record_number,
       u.name AS professional_name, pr.color AS professional_color,
       t.name AS type_name, t.color AS type_color,
       r.name AS resource_name
  FROM salud_appointments a
  JOIN salud_patients p ON p.id = a.patient_id
  JOIN clients c ON c.id = a.patient_id
  JOIN users u ON u.id = a.professional_id
  JOIN salud_professionals pr ON pr.user_id = a.professional_id
  JOIN salud_appointment_types t ON t.id = a.type_id
  LEFT JOIN salud_resources r ON r.id = a.resource_id;
--rollback DROP VIEW salud_v_appointments; DROP TABLE salud_appointments; DROP TABLE salud_blocks; DROP TABLE salud_schedules; DROP TABLE salud_appointment_types; DROP TABLE salud_resources; DROP TABLE salud_professionals;
