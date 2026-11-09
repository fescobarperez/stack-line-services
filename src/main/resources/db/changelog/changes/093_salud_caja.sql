--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Stackline Salud · Caja y seguimiento (fase 7).
--
--   salud_packages          catálogo de paquetes («10 sesiones de
--                           fisioterapia»): tipo de cita, sesiones, precio
--                           y vigencia.
--   salud_patient_packages  paquete vendido a un paciente. Se factura al
--                           venderlo; cada cita de ese tipo descuenta una
--                           sesión.
--   salud_credits           anticipos del paciente, como movimientos: entra
--                           el anticipo (facturado al recibirlo, como pide
--                           SAT), sale lo aplicado a una cita, vuelve si el
--                           cobro se anula. El saldo es la suma.
--   salud_charges           el cobro de una cita: factura (venta), paquete,
--                           anticipo, mixto o cortesía. Uno vigente por cita.
--   salud_treatment_plans   plan de tratamiento de un caso: N sesiones de un
--                           tipo, cada tantos días. Las citas del plan lo
--                           llevan (salud_appointments.plan_id).
--
-- Los controles (salud_follow_ups) ganan su recordatorio por WhatsApp unos
-- días antes de la fecha límite, y la respuesta del paciente («me siento
-- mejor / igual / peor») queda en el control, dentro de su caso.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:093-salud-caja
CREATE TABLE salud_packages (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT         NOT NULL REFERENCES companies (id),
    name           VARCHAR(100)   NOT NULL,
    type_id        BIGINT         NOT NULL REFERENCES salud_appointment_types (id),
    sessions       SMALLINT       NOT NULL,
    price          NUMERIC(12, 2) NOT NULL,
    -- días de vigencia desde la compra; sin valor no vence
    valid_days     SMALLINT,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_salud_packages_name UNIQUE (company_id, name),
    CONSTRAINT ck_salud_packages_sessions CHECK (sessions BETWEEN 1 AND 200),
    CONSTRAINT ck_salud_packages_price CHECK (price >= 0),
    CONSTRAINT ck_salud_packages_valid CHECK (valid_days IS NULL OR valid_days BETWEEN 1 AND 1095)
);

CREATE TABLE salud_patient_packages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT         NOT NULL REFERENCES companies (id),
    patient_id      BIGINT         NOT NULL REFERENCES salud_patients (id),
    package_id      BIGINT         REFERENCES salud_packages (id),
    -- copia del catálogo al venderlo: si mañana cambia el paquete, este no
    name            VARCHAR(100)   NOT NULL,
    type_id         BIGINT         NOT NULL REFERENCES salud_appointment_types (id),
    sessions_total  SMALLINT       NOT NULL,
    sessions_used   SMALLINT       NOT NULL DEFAULT 0,
    price           NUMERIC(12, 2) NOT NULL,
    sale_id         BIGINT         REFERENCES sales (id),
    purchased_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    expires_on      DATE,
    status          VARCHAR(10)    NOT NULL DEFAULT 'activo',
    created_by      BIGINT         REFERENCES users (id),
    CONSTRAINT ck_salud_ppk_status CHECK (status IN ('activo', 'agotado', 'anulado')),
    CONSTRAINT ck_salud_ppk_sessions CHECK (sessions_used BETWEEN 0 AND sessions_total)
);
CREATE INDEX ix_salud_ppk_patient ON salud_patient_packages (patient_id, status);

CREATE TABLE salud_credits (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT         NOT NULL REFERENCES companies (id),
    patient_id   BIGINT         NOT NULL REFERENCES salud_patients (id),
    kind         VARCHAR(10)    NOT NULL,
    -- con signo: + entra (anticipo, reverso), − sale (aplicado)
    amount       NUMERIC(12, 2) NOT NULL,
    sale_id      BIGINT         REFERENCES sales (id),
    charge_id    BIGINT,
    notes        VARCHAR(300),
    created_by   BIGINT         REFERENCES users (id),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_salud_credits_kind CHECK (kind IN ('anticipo', 'aplicado', 'reverso')),
    CONSTRAINT ck_salud_credits_sign CHECK ((kind = 'aplicado' AND amount < 0) OR (kind <> 'aplicado' AND amount > 0))
);
CREATE INDEX ix_salud_credits_patient ON salud_credits (patient_id, created_at DESC);

CREATE TABLE salud_treatment_plans (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT        NOT NULL REFERENCES companies (id),
    patient_id       BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    case_id          BIGINT        NOT NULL REFERENCES salud_cases (id) ON DELETE CASCADE,
    title            VARCHAR(120)  NOT NULL,
    type_id          BIGINT        NOT NULL REFERENCES salud_appointment_types (id),
    professional_id  BIGINT        REFERENCES users (id),
    sessions         SMALLINT      NOT NULL,
    interval_days    SMALLINT,
    notes            VARCHAR(300),
    status           VARCHAR(10)   NOT NULL DEFAULT 'activo',
    created_by       BIGINT        REFERENCES users (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ,
    CONSTRAINT ck_salud_plans_status CHECK (status IN ('activo', 'completado', 'cancelado')),
    CONSTRAINT ck_salud_plans_sessions CHECK (sessions BETWEEN 1 AND 100),
    CONSTRAINT ck_salud_plans_interval CHECK (interval_days IS NULL OR interval_days BETWEEN 1 AND 90)
);
CREATE INDEX ix_salud_plans_patient ON salud_treatment_plans (patient_id, status);

ALTER TABLE salud_appointments ADD COLUMN plan_id BIGINT REFERENCES salud_treatment_plans (id);
CREATE INDEX ix_salud_appt_plan ON salud_appointments (plan_id) WHERE plan_id IS NOT NULL;

CREATE TABLE salud_charges (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id          BIGINT         NOT NULL REFERENCES companies (id),
    appointment_id      BIGINT         NOT NULL REFERENCES salud_appointments (id),
    patient_id          BIGINT         NOT NULL REFERENCES salud_patients (id),
    type_id             BIGINT         NOT NULL REFERENCES salud_appointment_types (id),
    professional_id     BIGINT         NOT NULL REFERENCES users (id),
    method              VARCHAR(10)    NOT NULL,
    -- precio de lista, descuento y lo que pagó el paciente (anticipo + caja)
    price               NUMERIC(12, 2) NOT NULL,
    discount            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total               NUMERIC(12, 2) NOT NULL,
    credit_amount       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    cash_amount         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    -- lo que vale la atención para los reportes: con paquete, precio ÷ sesiones
    service_value       NUMERIC(12, 2) NOT NULL,
    payment_method      VARCHAR(20),
    sale_id             BIGINT         REFERENCES sales (id),
    patient_package_id  BIGINT         REFERENCES salud_patient_packages (id),
    status              VARCHAR(10)    NOT NULL DEFAULT 'vigente',
    notes               VARCHAR(300),
    created_by          BIGINT         REFERENCES users (id),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    voided_at           TIMESTAMPTZ,
    voided_by           BIGINT         REFERENCES users (id),
    void_reason         VARCHAR(300),
    CONSTRAINT ck_salud_charges_method CHECK (method IN ('venta', 'paquete', 'anticipo', 'mixto', 'cortesia')),
    CONSTRAINT ck_salud_charges_status CHECK (status IN ('vigente', 'anulado')),
    CONSTRAINT ck_salud_charges_amounts CHECK (price >= 0 AND discount >= 0 AND total >= 0
        AND credit_amount >= 0 AND cash_amount >= 0 AND credit_amount + cash_amount = total),
    CONSTRAINT ck_salud_charges_void CHECK (status = 'vigente' OR (voided_at IS NOT NULL AND void_reason IS NOT NULL))
);
-- una cita se cobra una vez; anulado el cobro, se puede volver a cobrar
CREATE UNIQUE INDEX uq_salud_charges_appt ON salud_charges (appointment_id) WHERE status = 'vigente';
CREATE INDEX ix_salud_charges_company ON salud_charges (company_id, created_at);
CREATE INDEX ix_salud_charges_patient ON salud_charges (patient_id, created_at DESC);
ALTER TABLE salud_credits ADD CONSTRAINT fk_salud_credits_charge FOREIGN KEY (charge_id) REFERENCES salud_charges (id);

-- Recordatorio del control y lo que contestó el paciente.
ALTER TABLE salud_follow_ups
    ADD COLUMN reminder_status    VARCHAR(10),
    ADD COLUMN reminder_attempts  SMALLINT     NOT NULL DEFAULT 0,
    ADD COLUMN reminder_next_at   TIMESTAMPTZ,
    ADD COLUMN reminder_sent_at   TIMESTAMPTZ,
    ADD COLUMN reminder_error     VARCHAR(500),
    ADD COLUMN feeling            VARCHAR(10),
    ADD COLUMN feeling_at         TIMESTAMPTZ,
    ADD COLUMN feeling_note       VARCHAR(500),
    ADD CONSTRAINT ck_salud_fu_reminder CHECK (reminder_status IN ('enviado', 'fallido', 'omitido')),
    ADD CONSTRAINT ck_salud_fu_feeling CHECK (feeling IN ('mejor', 'igual', 'peor'));

ALTER TABLE salud_settings
    ADD COLUMN control_reminders_enabled BOOLEAN  NOT NULL DEFAULT TRUE,
    ADD COLUMN control_reminder_days     SMALLINT NOT NULL DEFAULT 2,
    ADD CONSTRAINT ck_salud_settings_control_days CHECK (control_reminder_days BETWEEN 1 AND 14);

-- Controles que toca recordar ahora (todas las empresas; la usa el proceso
-- periódico). Condiciones:
--   · pendiente (sin cita), de un caso abierto, sin recordatorio todavía;
--   · faltan los días de anticipación de la clínica o menos, y no venció
--     hace más de una semana (pasado eso lo trabaja recepción);
--   · entre 8:00 y 19:00 de Guatemala: es un mensaje para preguntar cómo
--     sigue el paciente, no para despertarlo;
--   · el control no se creó hoy (la consulta acaba de terminar);
--   · el paciente está activo, acepta mensajes por WhatsApp y tiene teléfono;
--   · si un intento falló, ya pasó la espera para reintentar.
CREATE VIEW salud_v_control_reminders_due AS
SELECT f.id, f.company_id, f.patient_id, f.due_on, f.reminder_attempts, f.type_id, f.professional_id,
       c.name AS patient_name, c.phone AS patient_phone,
       u.name AS professional_name, t.name AS type_name, co.name AS company_name
  FROM salud_follow_ups f
  JOIN salud_cases k ON k.id = f.case_id AND k.status = 'abierto'
  JOIN salud_patients p ON p.id = f.patient_id
  JOIN clients c ON c.id = f.patient_id
  JOIN companies co ON co.id = f.company_id
  LEFT JOIN users u ON u.id = f.professional_id
  LEFT JOIN salud_appointment_types t ON t.id = f.type_id
  LEFT JOIN salud_settings s ON s.company_id = f.company_id
 WHERE f.status = 'pendiente'
   AND f.reminder_status IS NULL
   AND COALESCE(s.control_reminders_enabled, TRUE)
   AND f.due_on - COALESCE(s.control_reminder_days, 2) <= (now() AT TIME ZONE 'America/Guatemala')::date
   AND f.due_on >= (now() AT TIME ZONE 'America/Guatemala')::date - 7
   AND (now() AT TIME ZONE 'America/Guatemala')::time BETWEEN TIME '08:00' AND TIME '19:00'
   AND f.created_at < date_trunc('day', now() AT TIME ZONE 'America/Guatemala') AT TIME ZONE 'America/Guatemala'
   AND (f.reminder_next_at IS NULL OR f.reminder_next_at <= now())
   AND p.status = 'activo'
   AND p.accepts_messages
   AND p.reminder_channel = 'whatsapp'
   AND coalesce(c.phone, '') <> '';

-- La bandeja de controles de recepción: con nombres y teléfono.
CREATE VIEW salud_v_follow_ups AS
SELECT f.id, f.company_id, f.patient_id, f.case_id, f.professional_id, f.type_id, f.due_on, f.notes, f.status,
       f.appointment_id, f.reminder_status, f.reminder_sent_at, f.feeling, f.feeling_at, f.feeling_note,
       f.created_at,
       c.name AS patient_name, c.phone AS patient_phone, p.record_number,
       u.name AS professional_name, t.name AS type_name, k.title AS case_title, k.status AS case_status
  FROM salud_follow_ups f
  JOIN salud_cases k ON k.id = f.case_id
  JOIN salud_patients p ON p.id = f.patient_id
  JOIN clients c ON c.id = f.patient_id
  LEFT JOIN users u ON u.id = f.professional_id
  LEFT JOIN salud_appointment_types t ON t.id = f.type_id;

-- La agenda sabe si la cita es de un plan y si ya se cobró.
CREATE OR REPLACE VIEW salud_v_appointments AS
SELECT a.id, a.company_id, a.patient_id, a.professional_id, a.resource_id, a.type_id,
       a.starts_at, a.ends_at, a.status, a.source, a.reason, a.notes, a.cancel_reason,
       a.created_at, a.confirmed_at, a.arrived_at, a.started_at, a.completed_at, a.cancelled_at,
       c.name AS patient_name, c.phone AS patient_phone, p.record_number,
       u.name AS professional_name, pr.color AS professional_color,
       t.name AS type_name, t.color AS type_color,
       r.name AS resource_name,
       a.reminder_status, a.reminder_sent_at,
       a.case_id, e.id AS encounter_id, e.status AS encounter_status,
       a.plan_id, ch.id AS charge_id, ch.method AS charge_method, ch.total AS charge_total,
       t.price AS type_price
  FROM salud_appointments a
  JOIN salud_patients p ON p.id = a.patient_id
  JOIN clients c ON c.id = a.patient_id
  JOIN users u ON u.id = a.professional_id
  JOIN salud_professionals pr ON pr.user_id = a.professional_id
  JOIN salud_appointment_types t ON t.id = a.type_id
  LEFT JOIN salud_resources r ON r.id = a.resource_id
  LEFT JOIN salud_encounters e ON e.appointment_id = a.id
  LEFT JOIN salud_charges ch ON ch.appointment_id = a.id AND ch.status = 'vigente';
--rollback DROP VIEW salud_v_appointments; CREATE VIEW salud_v_appointments AS SELECT a.id, a.company_id, a.patient_id, a.professional_id, a.resource_id, a.type_id, a.starts_at, a.ends_at, a.status, a.source, a.reason, a.notes, a.cancel_reason, a.created_at, a.confirmed_at, a.arrived_at, a.started_at, a.completed_at, a.cancelled_at, c.name AS patient_name, c.phone AS patient_phone, p.record_number, u.name AS professional_name, pr.color AS professional_color, t.name AS type_name, t.color AS type_color, r.name AS resource_name, a.reminder_status, a.reminder_sent_at, a.case_id, e.id AS encounter_id, e.status AS encounter_status FROM salud_appointments a JOIN salud_patients p ON p.id = a.patient_id JOIN clients c ON c.id = a.patient_id JOIN users u ON u.id = a.professional_id JOIN salud_professionals pr ON pr.user_id = a.professional_id JOIN salud_appointment_types t ON t.id = a.type_id LEFT JOIN salud_resources r ON r.id = a.resource_id LEFT JOIN salud_encounters e ON e.appointment_id = a.id; DROP VIEW salud_v_follow_ups; DROP VIEW salud_v_control_reminders_due; ALTER TABLE salud_settings DROP CONSTRAINT ck_salud_settings_control_days, DROP COLUMN control_reminders_enabled, DROP COLUMN control_reminder_days; ALTER TABLE salud_follow_ups DROP CONSTRAINT ck_salud_fu_reminder, DROP CONSTRAINT ck_salud_fu_feeling, DROP COLUMN reminder_status, DROP COLUMN reminder_attempts, DROP COLUMN reminder_next_at, DROP COLUMN reminder_sent_at, DROP COLUMN reminder_error, DROP COLUMN feeling, DROP COLUMN feeling_at, DROP COLUMN feeling_note; ALTER TABLE salud_credits DROP CONSTRAINT fk_salud_credits_charge; DROP TABLE salud_charges; ALTER TABLE salud_appointments DROP COLUMN plan_id; DROP TABLE salud_treatment_plans; DROP TABLE salud_credits; DROP TABLE salud_patient_packages; DROP TABLE salud_packages;
