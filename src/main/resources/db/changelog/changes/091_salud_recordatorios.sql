--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Stackline Salud · Recordatorios de cita por WhatsApp.
--
-- Cada clínica decide si manda recordatorios y con cuántas horas de
-- anticipación (salud_settings; sin fila, sí y 24 h). El ERP encuentra las
-- citas que toca recordar (vista salud_v_reminders_due) y se las entrega a
-- agents-services, que escribe por el WhatsApp de la empresa con botones
-- para confirmar, reprogramar o cancelar.
--
-- En la cita queda el resultado: reminder_status 'enviado', 'fallido' (tras
-- los reintentos) u 'omitido'. Reprogramar la cita lo limpia: el recordatorio
-- era de la hora anterior.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:091-salud-recordatorios
CREATE TABLE salud_settings (
    company_id         BIGINT       PRIMARY KEY REFERENCES companies (id),
    reminders_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    reminder_hours     INTEGER      NOT NULL DEFAULT 24,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_salud_settings_hours CHECK (reminder_hours BETWEEN 2 AND 72)
);

ALTER TABLE salud_appointments
    ADD COLUMN reminder_status    VARCHAR(10),
    ADD COLUMN reminder_attempts  SMALLINT      NOT NULL DEFAULT 0,
    ADD COLUMN reminder_next_at   TIMESTAMPTZ,
    ADD COLUMN reminder_sent_at   TIMESTAMPTZ,
    ADD COLUMN reminder_error     VARCHAR(500),
    ADD CONSTRAINT ck_salud_appt_reminder CHECK (reminder_status IN ('enviado', 'fallido', 'omitido'));

-- Citas que toca recordar ahora, de todas las empresas (la usa el proceso
-- periódico, que corre sin usuario). Condiciones:
--   · solicitada o confirmada, sin recordatorio todavía;
--   · empieza dentro de las horas de anticipación de la clínica, pero no en
--     menos de 2 h (ya no sirve) ni la agendaron hace menos de 2 h (la
--     persona acaba de hablar con la clínica);
--   · el paciente está activo, acepta mensajes y prefiere WhatsApp, y su
--     cliente tiene teléfono;
--   · si un intento falló, ya pasó la espera para reintentar.
CREATE VIEW salud_v_reminders_due AS
SELECT a.id, a.company_id, a.patient_id, a.starts_at, a.ends_at, a.status, a.reminder_attempts,
       c.name AS patient_name, c.phone AS patient_phone,
       u.name AS professional_name, t.name AS type_name, r.name AS resource_name,
       co.name AS company_name
  FROM salud_appointments a
  JOIN salud_patients p ON p.id = a.patient_id
  JOIN clients c ON c.id = a.patient_id
  JOIN users u ON u.id = a.professional_id
  JOIN salud_appointment_types t ON t.id = a.type_id
  JOIN companies co ON co.id = a.company_id
  LEFT JOIN salud_resources r ON r.id = a.resource_id
  LEFT JOIN salud_settings s ON s.company_id = a.company_id
 WHERE a.status IN ('solicitada', 'confirmada')
   AND a.reminder_status IS NULL
   AND COALESCE(s.reminders_enabled, TRUE)
   AND a.starts_at > now() + interval '2 hours'
   AND a.starts_at <= now() + make_interval(hours => COALESCE(s.reminder_hours, 24))
   AND a.created_at <= now() - interval '2 hours'
   AND (a.reminder_next_at IS NULL OR a.reminder_next_at <= now())
   AND p.status = 'activo'
   AND p.accepts_messages
   AND p.reminder_channel = 'whatsapp'
   AND coalesce(c.phone, '') <> '';

-- La agenda muestra si ya se recordó la cita.
CREATE OR REPLACE VIEW salud_v_appointments AS
SELECT a.id, a.company_id, a.patient_id, a.professional_id, a.resource_id, a.type_id,
       a.starts_at, a.ends_at, a.status, a.source, a.reason, a.notes, a.cancel_reason,
       a.created_at, a.confirmed_at, a.arrived_at, a.started_at, a.completed_at, a.cancelled_at,
       c.name AS patient_name, c.phone AS patient_phone, p.record_number,
       u.name AS professional_name, pr.color AS professional_color,
       t.name AS type_name, t.color AS type_color,
       r.name AS resource_name,
       a.reminder_status, a.reminder_sent_at
  FROM salud_appointments a
  JOIN salud_patients p ON p.id = a.patient_id
  JOIN clients c ON c.id = a.patient_id
  JOIN users u ON u.id = a.professional_id
  JOIN salud_professionals pr ON pr.user_id = a.professional_id
  JOIN salud_appointment_types t ON t.id = a.type_id
  LEFT JOIN salud_resources r ON r.id = a.resource_id;
--rollback DROP VIEW salud_v_reminders_due; DROP VIEW salud_v_appointments; CREATE VIEW salud_v_appointments AS SELECT a.id, a.company_id, a.patient_id, a.professional_id, a.resource_id, a.type_id, a.starts_at, a.ends_at, a.status, a.source, a.reason, a.notes, a.cancel_reason, a.created_at, a.confirmed_at, a.arrived_at, a.started_at, a.completed_at, a.cancelled_at, c.name AS patient_name, c.phone AS patient_phone, p.record_number, u.name AS professional_name, pr.color AS professional_color, t.name AS type_name, t.color AS type_color, r.name AS resource_name FROM salud_appointments a JOIN salud_patients p ON p.id = a.patient_id JOIN clients c ON c.id = a.patient_id JOIN users u ON u.id = a.professional_id JOIN salud_professionals pr ON pr.user_id = a.professional_id JOIN salud_appointment_types t ON t.id = a.type_id LEFT JOIN salud_resources r ON r.id = a.resource_id; ALTER TABLE salud_appointments DROP CONSTRAINT ck_salud_appt_reminder, DROP COLUMN reminder_status, DROP COLUMN reminder_attempts, DROP COLUMN reminder_next_at, DROP COLUMN reminder_sent_at, DROP COLUMN reminder_error; DROP TABLE salud_settings;
