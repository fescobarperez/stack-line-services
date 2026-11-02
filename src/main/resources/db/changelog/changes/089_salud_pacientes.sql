--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Stackline Salud · Pacientes.
--
-- Un paciente ES un cliente: salud_patients usa el mismo id que clients
-- (llave primaria y foránea a la vez). Nombre, NIT, teléfono, correo y
-- dirección siguen viviendo en clients; aquí solo va lo que Cliente no
-- tiene. Así la FEL, la caja y el CRM lo tratan como a cualquier cliente y
-- ningún dato se guarda dos veces.
--
-- Lo clínico (salud_patient_clinical y las listas de alergias, antecedentes,
-- medicamentos y vacunas) va en tablas aparte de la ficha administrativa:
-- recepción ve la ficha sin ver lo clínico, y el servicio puede negar el
-- acceso por permiso sin tener que filtrar columnas.
--
-- ON DELETE RESTRICT hacia clients: un cliente que es paciente no se borra,
-- se inactiva. Su expediente es un registro clínico que debe conservarse.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:089-salud-pacientes
CREATE TABLE salud_patients (
    id                       BIGINT        PRIMARY KEY REFERENCES clients (id) ON DELETE RESTRICT,
    company_id               BIGINT        NOT NULL REFERENCES companies (id),
    record_number            VARCHAR(20)   NOT NULL,
    doc_type                 VARCHAR(12),
    doc_number               VARCHAR(30),
    preferred_name           VARCHAR(80),
    birth_date               DATE,
    sex                      VARCHAR(1),
    gender                   VARCHAR(40),
    marital_status           VARCHAR(20),
    nationality              VARCHAR(60),
    occupation               VARCHAR(100),
    education                VARCHAR(60),
    referral_source          VARCHAR(60),
    referred_by              VARCHAR(150),
    primary_professional_id  BIGINT        REFERENCES users (id),
    status                   VARCHAR(12)   NOT NULL DEFAULT 'activo',
    deceased_on              DATE,
    reminder_channel         VARCHAR(12)   NOT NULL DEFAULT 'whatsapp',
    accepts_messages         BOOLEAN       NOT NULL DEFAULT FALSE,
    insurer_name             VARCHAR(120),
    policy_number            VARCHAR(60),
    policy_expires_on        DATE,
    agreement_name           VARCHAR(120),
    admin_notes              VARCHAR(2000),
    specialty_data           JSONB         NOT NULL DEFAULT '{}',
    created_by               BIGINT        REFERENCES users (id),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_salud_patients_record UNIQUE (company_id, record_number),
    CONSTRAINT ck_salud_patients_doc CHECK ((doc_type IS NULL) = (doc_number IS NULL)),
    CONSTRAINT ck_salud_patients_doc_type CHECK (doc_type IN ('DPI', 'PASAPORTE', 'OTRO')),
    CONSTRAINT ck_salud_patients_sex CHECK (sex IN ('F', 'M', 'I')),
    CONSTRAINT ck_salud_patients_status CHECK (status IN ('activo', 'inactivo', 'fallecido')),
    CONSTRAINT ck_salud_patients_deceased CHECK (deceased_on IS NULL OR status = 'fallecido'),
    CONSTRAINT ck_salud_patients_channel CHECK (reminder_channel IN ('whatsapp', 'sms', 'email', 'ninguno'))
);
-- Un mismo documento no puede ser dos pacientes en la misma empresa.
CREATE UNIQUE INDEX uq_salud_patients_doc ON salud_patients (company_id, doc_type, doc_number)
    WHERE doc_number IS NOT NULL;
CREATE INDEX ix_salud_patients_company_status ON salud_patients (company_id, status);

-- Datos clínicos base del paciente (cambian poco). 1:1 con salud_patients.
CREATE TABLE salud_patient_clinical (
    patient_id          BIGINT         PRIMARY KEY REFERENCES salud_patients (id) ON DELETE CASCADE,
    company_id          BIGINT         NOT NULL REFERENCES companies (id),
    blood_type          VARCHAR(3),
    religion            VARCHAR(60),
    disability          VARCHAR(500),
    special_needs       VARCHAR(500),
    smoking             VARCHAR(200),
    alcohol             VARCHAR(200),
    physical_activity   VARCHAR(200),
    diet                VARCHAR(200),
    gyneco_obstetric    VARCHAR(1000),
    alerts              VARCHAR(500),
    advance_directives  VARCHAR(1000),
    updated_by          BIGINT         REFERENCES users (id),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_salud_clinical_blood CHECK (blood_type IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'))
);

CREATE TABLE salud_patient_allergies (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id   BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    kind         VARCHAR(12)   NOT NULL,
    allergen     VARCHAR(150)  NOT NULL,
    reaction     VARCHAR(300),
    severity     VARCHAR(10),
    notes        VARCHAR(500),
    CONSTRAINT ck_salud_allergy_kind CHECK (kind IN ('medicamento', 'alimento', 'latex', 'ambiental', 'otro')),
    CONSTRAINT ck_salud_allergy_severity CHECK (severity IN ('leve', 'moderada', 'severa'))
);
CREATE INDEX ix_salud_allergies_patient ON salud_patient_allergies (patient_id);

-- Antecedentes: enfermedades crónicas, cirugías, hospitalizaciones,
-- traumatismos, familiares y gineco-obstétricos en una sola tabla por tipo.
CREATE TABLE salud_patient_history (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id   BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    kind         VARCHAR(20)   NOT NULL,
    description  VARCHAR(500)  NOT NULL,
    cie10_code   VARCHAR(10),
    relative     VARCHAR(60),
    occurred     VARCHAR(60),
    notes        VARCHAR(500),
    CONSTRAINT ck_salud_history_kind CHECK (kind IN ('cronica', 'quirurgico', 'hospitalizacion', 'traumatico',
                                                     'familiar', 'gineco_obstetrico', 'otro')),
    CONSTRAINT ck_salud_history_relative CHECK (kind <> 'familiar' OR relative IS NOT NULL)
);
CREATE INDEX ix_salud_history_patient ON salud_patient_history (patient_id);

CREATE TABLE salud_patient_medications (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id   BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    name         VARCHAR(150)  NOT NULL,
    dose         VARCHAR(80),
    frequency    VARCHAR(80),
    since        VARCHAR(60),
    notes        VARCHAR(500)
);
CREATE INDEX ix_salud_medications_patient ON salud_patient_medications (patient_id);

CREATE TABLE salud_patient_vaccines (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id   BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    vaccine      VARCHAR(120)  NOT NULL,
    dose         VARCHAR(40),
    applied_on   DATE,
    notes        VARCHAR(300)
);
CREATE INDEX ix_salud_vaccines_patient ON salud_patient_vaccines (patient_id);

-- Responsables: tutor legal y/o quien paga. Siempre es otro cliente, para
-- que la factura salga a su nombre y NIT con el flujo normal de Stackline.
CREATE TABLE salud_patient_guardians (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id         BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    company_id         BIGINT        NOT NULL REFERENCES companies (id),
    client_id          BIGINT        NOT NULL REFERENCES clients (id),
    relationship       VARCHAR(40)   NOT NULL,
    is_legal_guardian  BOOLEAN       NOT NULL DEFAULT FALSE,
    is_payer           BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_salud_guardians UNIQUE (patient_id, client_id),
    CONSTRAINT ck_salud_guardians_self CHECK (patient_id <> client_id),
    CONSTRAINT ck_salud_guardians_role CHECK (is_legal_guardian OR is_payer)
);
-- Un solo pagador por paciente: si hubiera dos, la caja no sabría a quién facturar.
CREATE UNIQUE INDEX uq_salud_guardians_payer ON salud_patient_guardians (patient_id) WHERE is_payer;

-- Contactos de emergencia: solo nombre y teléfono, no son clientes.
CREATE TABLE salud_emergency_contacts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id    BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    name          VARCHAR(150)  NOT NULL,
    relationship  VARCHAR(40),
    phone         VARCHAR(40)   NOT NULL,
    sort_order    SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_salud_emergency_patient ON salud_emergency_contacts (patient_id);

-- Consentimientos: solo se agregan y se revocan, nunca se editan ni borran.
CREATE TABLE salud_patient_consents (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id    BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    kind          VARCHAR(20)   NOT NULL,
    version       VARCHAR(30),
    granted       BOOLEAN       NOT NULL,
    signed_at     TIMESTAMPTZ   NOT NULL,
    notes         VARCHAR(500),
    recorded_by   BIGINT        REFERENCES users (id),
    recorded_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMPTZ,
    revoked_by    BIGINT        REFERENCES users (id),
    CONSTRAINT ck_salud_consent_kind CHECK (kind IN ('datos', 'tratamiento', 'fotos', 'comunicacion', 'directivas', 'otro'))
);
CREATE INDEX ix_salud_consents_patient ON salud_patient_consents (patient_id);

-- Bitácora de accesos: quién abrió o cambió cada expediente. Solo se inserta.
CREATE TABLE salud_access_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    patient_id    BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    user_id       BIGINT        REFERENCES users (id),
    action        VARCHAR(20)   NOT NULL,
    occurred_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_salud_access_patient ON salud_access_log (patient_id, occurred_at DESC);

-- Listado de pacientes con los datos de su cliente (nombre, NIT, teléfono).
-- Vista de solo lectura: lo que se edita sigue yendo por ClientService.
CREATE VIEW salud_v_patients AS
SELECT p.id, p.company_id, p.record_number, p.doc_type, p.doc_number, p.birth_date, p.sex,
       p.status, p.primary_professional_id, p.created_at,
       c.name, c.nit, c.phone, c.email,
       regexp_replace(coalesce(c.phone, ''), '[^0-9]', '', 'g') AS phone_digits
  FROM salud_patients p
  JOIN clients c ON c.id = p.id AND c.company_id = p.company_id;

CREATE VIEW salud_v_access_log AS
SELECT l.id, l.company_id, l.patient_id, l.user_id, u.name AS user_name, l.action, l.occurred_at
  FROM salud_access_log l
  LEFT JOIN users u ON u.id = l.user_id;
--rollback DROP VIEW salud_v_access_log; DROP VIEW salud_v_patients; DROP TABLE salud_access_log; DROP TABLE salud_patient_consents; DROP TABLE salud_emergency_contacts; DROP TABLE salud_patient_guardians; DROP TABLE salud_patient_vaccines; DROP TABLE salud_patient_medications; DROP TABLE salud_patient_history; DROP TABLE salud_patient_allergies; DROP TABLE salud_patient_clinical; DROP TABLE salud_patients;
