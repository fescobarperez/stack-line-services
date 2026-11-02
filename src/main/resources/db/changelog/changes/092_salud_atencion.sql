--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Stackline Salud · Atención clínica (fase 6).
--
--   salud_cases           caso clínico: el problema que une las consultas
--                         («Migraña», abierto el 01/11). Se cierra cuando
--                         se resuelve.
--   salud_encounters      la consulta: nota SOAP, signos vitales, respuesta
--                         al tratamiento y plan de seguimiento. Nace ligada
--                         a una cita; al firmarse queda cerrada y solo
--                         admite adendas.
--   salud_encounter_diagnoses / salud_prescription_items / salud_encounter_addenda
--   salud_follow_ups      control pendiente («en 15 días») que se agenda
--                         como cita; al atenderla queda cumplido.
--   salud_cie10           catálogo de diagnósticos (común a todas las
--                         empresas). Trae los frecuentes en consulta
--                         general; el completo se importa aparte.
--
-- Todo es dato clínico: lo ve solo quien tiene el permiso clinical_record.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:092-salud-atencion
CREATE TABLE salud_cie10 (
    code         VARCHAR(10)   PRIMARY KEY,
    description  VARCHAR(255)  NOT NULL,
    -- minúsculas y sin tildes, para buscar «cefalea» o «diabetes» como sea que se escriban
    search_text  VARCHAR(300)  NOT NULL
);
CREATE INDEX ix_salud_cie10_search ON salud_cie10 (search_text);

CREATE TABLE salud_cases (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    patient_id    BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    title         VARCHAR(150)  NOT NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'abierto',
    opened_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    opened_by     BIGINT        REFERENCES users (id),
    closed_at     TIMESTAMPTZ,
    closed_by     BIGINT        REFERENCES users (id),
    outcome       VARCHAR(1000),
    CONSTRAINT ck_salud_cases_status CHECK (status IN ('abierto', 'cerrado'))
);
CREATE INDEX ix_salud_cases_patient ON salud_cases (patient_id, status, opened_at DESC);

CREATE TABLE salud_encounters (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL REFERENCES companies (id),
    patient_id         BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    appointment_id     BIGINT        REFERENCES salud_appointments (id),
    professional_id    BIGINT        NOT NULL REFERENCES users (id),
    case_id            BIGINT        REFERENCES salud_cases (id),
    -- caso nuevo propuesto en el borrador; se crea al firmar
    new_case_title     VARCHAR(150),
    status             VARCHAR(10)   NOT NULL DEFAULT 'borrador',
    -- SOAP
    subjective         TEXT,
    objective          TEXT,
    assessment         TEXT,
    plan               TEXT,
    instructions       VARCHAR(2000),
    -- signos vitales
    bp_systolic        SMALLINT,
    bp_diastolic       SMALLINT,
    heart_rate         SMALLINT,
    resp_rate          SMALLINT,
    temperature        NUMERIC(4,1),
    spo2               SMALLINT,
    weight_kg          NUMERIC(5,2),
    height_cm          NUMERIC(5,1),
    pain_score         SMALLINT,
    -- control: cómo respondió al tratamiento
    response           VARCHAR(10),
    adherence          VARCHAR(10),
    adverse_effects    VARCHAR(1000),
    -- plan de seguimiento (se vuelve salud_follow_ups al firmar)
    follow_up_days     SMALLINT,
    follow_up_type_id  BIGINT        REFERENCES salud_appointment_types (id),
    follow_up_notes    VARCHAR(300),
    created_by         BIGINT        REFERENCES users (id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    signed_at          TIMESTAMPTZ,
    signed_by          BIGINT        REFERENCES users (id),
    CONSTRAINT uq_salud_encounter_appt UNIQUE (appointment_id),
    CONSTRAINT ck_salud_enc_status CHECK (status IN ('borrador', 'firmada')),
    CONSTRAINT ck_salud_enc_response CHECK (response IN ('mejoro', 'igual', 'empeoro')),
    CONSTRAINT ck_salud_enc_adherence CHECK (adherence IN ('completa', 'parcial', 'no')),
    CONSTRAINT ck_salud_enc_pain CHECK (pain_score BETWEEN 0 AND 10),
    CONSTRAINT ck_salud_enc_followup CHECK (follow_up_days BETWEEN 1 AND 365),
    -- firmada = tiene caso y fecha de firma
    CONSTRAINT ck_salud_enc_signed CHECK (status = 'borrador' OR (signed_at IS NOT NULL AND case_id IS NOT NULL))
);
CREATE INDEX ix_salud_encounters_patient ON salud_encounters (patient_id, created_at DESC);
CREATE INDEX ix_salud_encounters_case ON salud_encounters (case_id, created_at);

CREATE TABLE salud_encounter_diagnoses (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    encounter_id  BIGINT        NOT NULL REFERENCES salud_encounters (id) ON DELETE CASCADE,
    code          VARCHAR(10),
    description   VARCHAR(255)  NOT NULL,
    is_primary    BOOLEAN       NOT NULL DEFAULT FALSE,
    position      SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_salud_enc_dx ON salud_encounter_diagnoses (encounter_id, position);

CREATE TABLE salud_prescription_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    encounter_id   BIGINT        NOT NULL REFERENCES salud_encounters (id) ON DELETE CASCADE,
    medication     VARCHAR(150)  NOT NULL,
    dose           VARCHAR(60),
    route          VARCHAR(40),
    frequency      VARCHAR(60),
    duration_days  SMALLINT,
    quantity       VARCHAR(40),
    instructions   VARCHAR(300),
    position       SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_salud_rx_days CHECK (duration_days BETWEEN 1 AND 365)
);
CREATE INDEX ix_salud_rx ON salud_prescription_items (encounter_id, position);

CREATE TABLE salud_encounter_addenda (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    encounter_id  BIGINT        NOT NULL REFERENCES salud_encounters (id) ON DELETE CASCADE,
    author_id     BIGINT        REFERENCES users (id),
    body          VARCHAR(2000) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_salud_addenda ON salud_encounter_addenda (encounter_id, created_at);

CREATE TABLE salud_follow_ups (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT        NOT NULL REFERENCES companies (id),
    patient_id      BIGINT        NOT NULL REFERENCES salud_patients (id) ON DELETE CASCADE,
    case_id         BIGINT        NOT NULL REFERENCES salud_cases (id) ON DELETE CASCADE,
    encounter_id    BIGINT        NOT NULL REFERENCES salud_encounters (id) ON DELETE CASCADE,
    professional_id BIGINT        REFERENCES users (id),
    type_id         BIGINT        REFERENCES salud_appointment_types (id),
    due_on          DATE          NOT NULL,
    notes           VARCHAR(300),
    status          VARCHAR(10)   NOT NULL DEFAULT 'pendiente',
    appointment_id  BIGINT        REFERENCES salud_appointments (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    CONSTRAINT ck_salud_fu_status CHECK (status IN ('pendiente', 'agendado', 'cumplido', 'cancelado'))
);
CREATE INDEX ix_salud_fu_patient ON salud_follow_ups (patient_id, status, due_on);
CREATE INDEX ix_salud_fu_due ON salud_follow_ups (company_id, status, due_on);

-- La cita de control lleva el caso al que pertenece.
ALTER TABLE salud_appointments ADD COLUMN case_id BIGINT REFERENCES salud_cases (id);

-- Consultas con lo que se lista en el expediente: profesional, caso y
-- diagnóstico principal.
CREATE VIEW salud_v_encounters AS
SELECT e.id, e.company_id, e.patient_id, e.appointment_id, e.professional_id, e.case_id, e.status,
       e.created_at, e.signed_at, e.response, e.pain_score,
       e.bp_systolic, e.bp_diastolic, e.heart_rate, e.temperature, e.spo2, e.weight_kg, e.height_cm,
       u.name AS professional_name,
       c.title AS case_title,
       dx.code AS dx_code, dx.description AS dx_description,
       (SELECT count(*) FROM salud_prescription_items r WHERE r.encounter_id = e.id) AS rx_count
  FROM salud_encounters e
  JOIN users u ON u.id = e.professional_id
  LEFT JOIN salud_cases c ON c.id = e.case_id
  LEFT JOIN LATERAL (
       SELECT d.code, d.description FROM salud_encounter_diagnoses d
        WHERE d.encounter_id = e.id ORDER BY d.is_primary DESC, d.position LIMIT 1) dx ON TRUE;

-- La agenda sabe si la cita ya tiene consulta y a qué caso pertenece.
CREATE OR REPLACE VIEW salud_v_appointments AS
SELECT a.id, a.company_id, a.patient_id, a.professional_id, a.resource_id, a.type_id,
       a.starts_at, a.ends_at, a.status, a.source, a.reason, a.notes, a.cancel_reason,
       a.created_at, a.confirmed_at, a.arrived_at, a.started_at, a.completed_at, a.cancelled_at,
       c.name AS patient_name, c.phone AS patient_phone, p.record_number,
       u.name AS professional_name, pr.color AS professional_color,
       t.name AS type_name, t.color AS type_color,
       r.name AS resource_name,
       a.reminder_status, a.reminder_sent_at,
       a.case_id, e.id AS encounter_id, e.status AS encounter_status
  FROM salud_appointments a
  JOIN salud_patients p ON p.id = a.patient_id
  JOIN clients c ON c.id = a.patient_id
  JOIN users u ON u.id = a.professional_id
  JOIN salud_professionals pr ON pr.user_id = a.professional_id
  JOIN salud_appointment_types t ON t.id = a.type_id
  LEFT JOIN salud_resources r ON r.id = a.resource_id
  LEFT JOIN salud_encounters e ON e.appointment_id = a.id;
--rollback DROP VIEW salud_v_appointments; CREATE VIEW salud_v_appointments AS SELECT a.id, a.company_id, a.patient_id, a.professional_id, a.resource_id, a.type_id, a.starts_at, a.ends_at, a.status, a.source, a.reason, a.notes, a.cancel_reason, a.created_at, a.confirmed_at, a.arrived_at, a.started_at, a.completed_at, a.cancelled_at, c.name AS patient_name, c.phone AS patient_phone, p.record_number, u.name AS professional_name, pr.color AS professional_color, t.name AS type_name, t.color AS type_color, r.name AS resource_name, a.reminder_status, a.reminder_sent_at FROM salud_appointments a JOIN salud_patients p ON p.id = a.patient_id JOIN clients c ON c.id = a.patient_id JOIN users u ON u.id = a.professional_id JOIN salud_professionals pr ON pr.user_id = a.professional_id JOIN salud_appointment_types t ON t.id = a.type_id LEFT JOIN salud_resources r ON r.id = a.resource_id; DROP VIEW salud_v_encounters; ALTER TABLE salud_appointments DROP COLUMN case_id; DROP TABLE salud_follow_ups; DROP TABLE salud_encounter_addenda; DROP TABLE salud_prescription_items; DROP TABLE salud_encounter_diagnoses; DROP TABLE salud_encounters; DROP TABLE salud_cases; DROP TABLE salud_cie10;

--changeset erp_maya:092-salud-cie10-frecuentes
-- Diagnósticos frecuentes en consulta general (CIE-10, OPS). El catálogo
-- completo se carga con el mismo formato (code, description).
INSERT INTO salud_cie10 (code, description, search_text)
SELECT code, description, translate(lower(code || ' ' || description), 'áéíóúüñ', 'aeiouun')
  FROM (VALUES
    ('A00.9', 'Cólera, no especificado'),
    ('A01.0', 'Fiebre tifoidea'),
    ('A04.9', 'Infección intestinal bacteriana, no especificada'),
    ('A06.0', 'Disentería amebiana aguda'),
    ('A07.1', 'Giardiasis [lambliasis]'),
    ('A08.4', 'Infección intestinal viral, sin otra especificación'),
    ('A09', 'Diarrea y gastroenteritis de presunto origen infeccioso'),
    ('A15.0', 'Tuberculosis del pulmón, confirmada por hallazgo microscópico'),
    ('A37.9', 'Tos ferina, no especificada'),
    ('A46', 'Erisipela'),
    ('A63.0', 'Verrugas (venéreas) anogenitales'),
    ('A90', 'Fiebre del dengue [dengue clásico]'),
    ('A91', 'Fiebre del dengue hemorrágico'),
    ('A92.0', 'Enfermedad por virus Chikungunya'),
    ('A92.5', 'Enfermedad por virus Zika'),
    ('B00.1', 'Dermatitis vesicular debida a virus del herpes simple'),
    ('B01.9', 'Varicela sin complicaciones'),
    ('B02.9', 'Herpes zóster sin complicaciones'),
    ('B05.9', 'Sarampión sin complicaciones'),
    ('B07', 'Verrugas víricas'),
    ('B08.4', 'Estomatitis vesicular enteroviral con exantema'),
    ('B15.9', 'Hepatitis aguda tipo A, sin coma hepático'),
    ('B16.9', 'Hepatitis aguda tipo B, sin agente delta y sin coma hepático'),
    ('B18.1', 'Hepatitis viral tipo B crónica, sin agente delta'),
    ('B18.2', 'Hepatitis viral tipo C crónica'),
    ('B20', 'Enfermedad por VIH, resultante en enfermedades infecciosas y parasitarias'),
    ('B27.9', 'Mononucleosis infecciosa, no especificada'),
    ('B34.9', 'Infección viral, no especificada'),
    ('B35.1', 'Tiña de las uñas'),
    ('B35.3', 'Tiña del pie [tinea pedis]'),
    ('B35.4', 'Tiña del cuerpo [tinea corporis]'),
    ('B36.0', 'Pitiriasis versicolor'),
    ('B37.0', 'Estomatitis candidiásica'),
    ('B37.3', 'Candidiasis de la vulva y de la vagina'),
    ('B77.9', 'Ascariasis, no especificada'),
    ('B82.9', 'Parasitosis intestinal, sin otra especificación'),
    ('B85.0', 'Pediculosis debida a Pediculus humanus capitis'),
    ('B86', 'Escabiosis'),
    ('C16.9', 'Tumor maligno del estómago, parte no especificada'),
    ('C34.9', 'Tumor maligno de los bronquios o del pulmón, parte no especificada'),
    ('C50.9', 'Tumor maligno de la mama, parte no especificada'),
    ('C53.9', 'Tumor maligno del cuello del útero, sin otra especificación'),
    ('C61', 'Tumor maligno de la próstata'),
    ('D25.9', 'Leiomioma del útero, sin otra especificación'),
    ('D50.9', 'Anemia por deficiencia de hierro, sin otra especificación'),
    ('D64.9', 'Anemia de tipo no especificado'),
    ('E03.9', 'Hipotiroidismo, no especificado'),
    ('E04.9', 'Bocio no tóxico, no especificado'),
    ('E05.9', 'Tirotoxicosis, hipertiroidismo no especificado'),
    ('E10.9', 'Diabetes mellitus tipo 1, sin mención de complicación'),
    ('E11.6', 'Diabetes mellitus tipo 2, con otras complicaciones especificadas'),
    ('E11.9', 'Diabetes mellitus tipo 2, sin mención de complicación'),
    ('E14.9', 'Diabetes mellitus, no especificada, sin mención de complicación'),
    ('E28.2', 'Síndrome de ovario poliquístico'),
    ('E44.0', 'Desnutrición proteicocalórica moderada'),
    ('E46', 'Desnutrición proteicocalórica, no especificada'),
    ('E55.9', 'Deficiencia de vitamina D, no especificada'),
    ('E66.9', 'Obesidad, no especificada'),
    ('E78.0', 'Hipercolesterolemia pura'),
    ('E78.1', 'Hipergliceridemia pura'),
    ('E78.5', 'Hiperlipidemia, no especificada'),
    ('E79.0', 'Hiperuricemia sin signos de artritis inflamatoria'),
    ('E86', 'Depleción del volumen (deshidratación)'),
    ('E87.6', 'Hipopotasemia'),
    ('F10.2', 'Trastornos mentales y del comportamiento debidos al uso de alcohol: síndrome de dependencia'),
    ('F17.2', 'Trastornos debidos al uso de tabaco: síndrome de dependencia'),
    ('F32.9', 'Episodio depresivo, no especificado'),
    ('F33.9', 'Trastorno depresivo recurrente, no especificado'),
    ('F41.0', 'Trastorno de pánico [ansiedad paroxística episódica]'),
    ('F41.1', 'Trastorno de ansiedad generalizada'),
    ('F41.9', 'Trastorno de ansiedad, no especificado'),
    ('F43.1', 'Trastorno de estrés postraumático'),
    ('F43.2', 'Trastornos de adaptación'),
    ('F51.0', 'Insomnio no orgánico'),
    ('F90.0', 'Perturbación de la actividad y de la atención'),
    ('G40.9', 'Epilepsia, tipo no especificado'),
    ('G43.0', 'Migraña sin aura [migraña común]'),
    ('G43.1', 'Migraña con aura [migraña clásica]'),
    ('G43.9', 'Migraña, no especificada'),
    ('G44.2', 'Cefalea debida a tensión'),
    ('G47.0', 'Trastornos del inicio y del mantenimiento del sueño [insomnios]'),
    ('G47.3', 'Apnea del sueño'),
    ('G51.0', 'Parálisis de Bell'),
    ('G56.0', 'Síndrome del túnel carpiano'),
    ('G62.9', 'Polineuropatía, no especificada'),
    ('H00.0', 'Orzuelo y otras inflamaciones profundas del párpado'),
    ('H01.0', 'Blefaritis'),
    ('H10.1', 'Conjuntivitis atópica aguda'),
    ('H10.9', 'Conjuntivitis, no especificada'),
    ('H25.9', 'Catarata senil, no especificada'),
    ('H40.9', 'Glaucoma, no especificado'),
    ('H52.1', 'Miopía'),
    ('H52.4', 'Presbicia'),
    ('H60.9', 'Otitis externa, sin otra especificación'),
    ('H61.2', 'Cerumen impactado'),
    ('H65.9', 'Otitis media no supurativa, sin otra especificación'),
    ('H66.9', 'Otitis media, no especificada'),
    ('H81.1', 'Vértigo paroxístico benigno'),
    ('H81.3', 'Otros vértigos periféricos'),
    ('H91.9', 'Hipoacusia, no especificada'),
    ('I10', 'Hipertensión esencial (primaria)'),
    ('I11.9', 'Enfermedad cardíaca hipertensiva sin insuficiencia cardíaca (congestiva)'),
    ('I20.9', 'Angina de pecho, no especificada'),
    ('I21.9', 'Infarto agudo del miocardio, sin otra especificación'),
    ('I25.9', 'Enfermedad isquémica crónica del corazón, no especificada'),
    ('I48', 'Fibrilación y aleteo auricular'),
    ('I49.9', 'Arritmia cardíaca, no especificada'),
    ('I50.9', 'Insuficiencia cardíaca, no especificada'),
    ('I63.9', 'Infarto cerebral, no especificado'),
    ('I64', 'Accidente vascular encefálico agudo, no especificado como hemorrágico o isquémico'),
    ('I80.2', 'Flebitis y tromboflebitis de otros vasos profundos de los miembros inferiores'),
    ('I83.9', 'Venas varicosas de los miembros inferiores sin úlcera ni inflamación'),
    ('I84.9', 'Hemorroides no especificadas, sin complicación'),
    ('I95.9', 'Hipotensión, no especificada'),
    ('J00', 'Rinofaringitis aguda [resfriado común]'),
    ('J01.9', 'Sinusitis aguda, no especificada'),
    ('J02.0', 'Faringitis estreptocócica'),
    ('J02.9', 'Faringitis aguda, no especificada'),
    ('J03.9', 'Amigdalitis aguda, no especificada'),
    ('J04.0', 'Laringitis aguda'),
    ('J06.9', 'Infección aguda de las vías respiratorias superiores, no especificada'),
    ('J10.1', 'Influenza con otras manifestaciones respiratorias, virus de la influenza identificado'),
    ('J11.1', 'Influenza con otras manifestaciones respiratorias, virus no identificado'),
    ('J12.9', 'Neumonía viral, no especificada'),
    ('J15.9', 'Neumonía bacteriana, no especificada'),
    ('J18.9', 'Neumonía, no especificada'),
    ('J20.9', 'Bronquitis aguda, no especificada'),
    ('J21.9', 'Bronquiolitis aguda, no especificada'),
    ('J30.4', 'Rinitis alérgica, no especificada'),
    ('J31.0', 'Rinitis crónica'),
    ('J32.9', 'Sinusitis crónica, no especificada'),
    ('J35.0', 'Amigdalitis crónica'),
    ('J40', 'Bronquitis, no especificada como aguda o crónica'),
    ('J44.9', 'Enfermedad pulmonar obstructiva crónica, no especificada'),
    ('J45.0', 'Asma predominantemente alérgica'),
    ('J45.9', 'Asma, no especificada'),
    ('K02.9', 'Caries dental, no especificada'),
    ('K04.0', 'Pulpitis'),
    ('K05.1', 'Gingivitis crónica'),
    ('K08.1', 'Pérdida de dientes debida a accidente, extracción o enfermedad periodontal local'),
    ('K12.0', 'Estomatitis aftosa recurrente'),
    ('K21.0', 'Enfermedad del reflujo gastroesofágico con esofagitis'),
    ('K21.9', 'Enfermedad del reflujo gastroesofágico sin esofagitis'),
    ('K25.9', 'Úlcera gástrica, no especificada como aguda ni crónica, sin hemorragia ni perforación'),
    ('K26.9', 'Úlcera duodenal, no especificada como aguda ni crónica, sin hemorragia ni perforación'),
    ('K29.5', 'Gastritis crónica, no especificada'),
    ('K29.7', 'Gastritis, no especificada'),
    ('K30', 'Dispepsia funcional'),
    ('K35.8', 'Apendicitis aguda, otra y la no especificada'),
    ('K40.9', 'Hernia inguinal unilateral o no especificada, sin obstrucción ni gangrena'),
    ('K42.9', 'Hernia umbilical sin obstrucción ni gangrena'),
    ('K52.9', 'Gastroenteritis y colitis no infecciosas, no especificadas'),
    ('K57.3', 'Enfermedad diverticular del intestino grueso sin perforación ni absceso'),
    ('K58.9', 'Síndrome del colon irritable sin diarrea'),
    ('K59.0', 'Constipación'),
    ('K60.2', 'Fisura anal, no especificada'),
    ('K64.9', 'Hemorroides, no especificadas'),
    ('K70.3', 'Cirrosis hepática alcohólica'),
    ('K74.6', 'Otras cirrosis del hígado y las no especificadas'),
    ('K76.0', 'Degeneración grasa del hígado, no clasificada en otra parte'),
    ('K80.2', 'Cálculo de la vesícula biliar sin colecistitis'),
    ('K81.9', 'Colecistitis, no especificada'),
    ('K85.9', 'Pancreatitis aguda, no especificada'),
    ('L01.0', 'Impétigo [cualquier sitio anatómico] [cualquier organismo]'),
    ('L02.9', 'Absceso cutáneo, furúnculo y ántrax, de sitio no especificado'),
    ('L03.9', 'Celulitis de sitio no especificado'),
    ('L08.9', 'Infección local de la piel y del tejido subcutáneo, no especificada'),
    ('L20.9', 'Dermatitis atópica, no especificada'),
    ('L21.9', 'Dermatitis seborreica, no especificada'),
    ('L22', 'Dermatitis del pañal'),
    ('L23.9', 'Dermatitis alérgica de contacto, de causa no especificada'),
    ('L25.9', 'Dermatitis de contacto, forma y causa no especificadas'),
    ('L30.9', 'Dermatitis, no especificada'),
    ('L40.0', 'Psoriasis vulgar'),
    ('L50.9', 'Urticaria, no especificada'),
    ('L60.0', 'Uña encarnada'),
    ('L63.9', 'Alopecia areata, no especificada'),
    ('L65.9', 'Pérdida del cabello no cicatricial, no especificada'),
    ('L70.0', 'Acné vulgar'),
    ('L72.1', 'Quiste tricolémico [quiste sebáceo]'),
    ('L80', 'Vitiligo'),
    ('M05.9', 'Artritis reumatoide seropositiva, sin otra especificación'),
    ('M06.9', 'Artritis reumatoide, no especificada'),
    ('M10.9', 'Gota, no especificada'),
    ('M13.9', 'Artritis, no especificada'),
    ('M16.9', 'Coxartrosis, no especificada'),
    ('M17.9', 'Gonartrosis, no especificada'),
    ('M19.9', 'Artrosis, no especificada'),
    ('M25.5', 'Dolor en articulación'),
    ('M41.9', 'Escoliosis, no especificada'),
    ('M47.9', 'Espondilosis, no especificada'),
    ('M51.2', 'Otros desplazamientos especificados de disco intervertebral'),
    ('M53.1', 'Síndrome cervicobraquial'),
    ('M54.2', 'Cervicalgia'),
    ('M54.4', 'Lumbago con ciática'),
    ('M54.5', 'Lumbago no especificado'),
    ('M54.9', 'Dorsalgia, no especificada'),
    ('M62.6', 'Distensión muscular'),
    ('M65.4', 'Tenosinovitis de estiloides radial [de Quervain]'),
    ('M75.1', 'Síndrome del manguito rotatorio'),
    ('M75.4', 'Síndrome de abducción dolorosa del hombro'),
    ('M77.1', 'Epicondilitis lateral'),
    ('M79.1', 'Mialgia'),
    ('M79.6', 'Dolor en miembro'),
    ('M79.7', 'Fibromialgia'),
    ('M81.9', 'Osteoporosis, no especificada'),
    ('N10', 'Nefritis tubulointersticial aguda'),
    ('N18.9', 'Enfermedad renal crónica, no especificada'),
    ('N20.0', 'Cálculo del riñón'),
    ('N23', 'Cólico renal, no especificado'),
    ('N30.0', 'Cistitis aguda'),
    ('N39.0', 'Infección de vías urinarias, sitio no especificado'),
    ('N40', 'Hiperplasia de la próstata'),
    ('N41.0', 'Prostatitis aguda'),
    ('N63', 'Masa no especificada en la mama'),
    ('N64.4', 'Mastodinia'),
    ('N76.0', 'Vaginitis aguda'),
    ('N77.1', 'Vaginitis, vulvitis y vulvovaginitis en enfermedades infecciosas y parasitarias clasificadas en otra parte'),
    ('N89.8', 'Otros trastornos no inflamatorios especificados de la vagina'),
    ('N91.2', 'Amenorrea, sin otra especificación'),
    ('N92.0', 'Menstruación excesiva y frecuente con ciclo regular'),
    ('N92.6', 'Menstruación irregular, no especificada'),
    ('N94.6', 'Dismenorrea, no especificada'),
    ('N95.1', 'Estados menopáusicos y climatéricos femeninos'),
    ('N97.9', 'Infertilidad femenina, no especificada'),
    ('O03.9', 'Aborto espontáneo completo o no especificado, sin complicación'),
    ('O13', 'Hipertensión gestacional [inducida por el embarazo] sin proteinuria significativa'),
    ('O14.9', 'Preeclampsia, no especificada'),
    ('O21.0', 'Hiperemesis gravídica leve'),
    ('O24.4', 'Diabetes mellitus que se origina con el embarazo'),
    ('O80.9', 'Parto único espontáneo, sin otra especificación'),
    ('P59.9', 'Ictericia neonatal, no especificada'),
    ('Q21.0', 'Defecto del tabique ventricular'),
    ('R00.0', 'Taquicardia, no especificada'),
    ('R00.2', 'Palpitaciones'),
    ('R03.0', 'Lectura elevada de la presión sanguínea, sin diagnóstico de hipertensión'),
    ('R05', 'Tos'),
    ('R06.0', 'Disnea'),
    ('R07.4', 'Dolor en el pecho, no especificado'),
    ('R10.1', 'Dolor abdominal localizado en parte superior'),
    ('R10.4', 'Otros dolores abdominales y los no especificados'),
    ('R11', 'Náusea y vómito'),
    ('R12', 'Acidez'),
    ('R14', 'Flatulencia y afecciones afines'),
    ('R19.7', 'Diarrea, no especificada'),
    ('R21', 'Salpullido y otras erupciones cutáneas no específicas'),
    ('R30.0', 'Disuria'),
    ('R31', 'Hematuria, no especificada'),
    ('R35', 'Poliuria'),
    ('R42', 'Mareo y desvanecimiento'),
    ('R50.9', 'Fiebre, no especificada'),
    ('R51', 'Cefalea'),
    ('R52.9', 'Dolor, no especificado'),
    ('R53', 'Malestar y fatiga'),
    ('R55', 'Síncope y colapso'),
    ('R56.0', 'Convulsiones febriles'),
    ('R60.0', 'Edema localizado'),
    ('R63.0', 'Anorexia'),
    ('R63.4', 'Pérdida anormal de peso'),
    ('R73.0', 'Anormalidades en la prueba de tolerancia a la glucosa'),
    ('R73.9', 'Hiperglucemia, no especificada'),
    ('S00.9', 'Traumatismo superficial de la cabeza, parte no especificada'),
    ('S01.9', 'Herida de la cabeza, parte no especificada'),
    ('S06.0', 'Concusión'),
    ('S13.4', 'Esguince y torcedura de la columna cervical'),
    ('S42.0', 'Fractura de la clavícula'),
    ('S52.5', 'Fractura de la epífisis inferior del radio'),
    ('S61.9', 'Herida de la muñeca y de la mano, parte no especificada'),
    ('S62.6', 'Fractura de otro dedo de la mano'),
    ('S63.5', 'Esguince y torcedura de la muñeca'),
    ('S80.0', 'Contusión de la rodilla'),
    ('S82.6', 'Fractura del maléolo externo'),
    ('S83.6', 'Esguince y torcedura de otras partes y las no especificadas de la rodilla'),
    ('S93.4', 'Esguince y torcedura del tobillo'),
    ('T14.0', 'Traumatismo superficial de región no especificada del cuerpo'),
    ('T14.1', 'Herida de región no especificada del cuerpo'),
    ('T30.0', 'Quemadura de región del cuerpo y grado no especificados'),
    ('T63.4', 'Efecto tóxico del veneno de otros artrópodos'),
    ('T78.3', 'Edema angioneurótico'),
    ('T78.4', 'Alergia no especificada'),
    ('T88.7', 'Efecto adverso no especificado de droga o medicamento'),
    ('W54', 'Mordedura o ataque de perro'),
    ('Z00.0', 'Examen médico general'),
    ('Z00.1', 'Control de salud de rutina del niño'),
    ('Z01.4', 'Examen ginecológico (general) (de rutina)'),
    ('Z09.9', 'Examen de seguimiento consecutivo a tratamiento no especificado'),
    ('Z23', 'Necesidad de inmunización contra enfermedad bacteriana única'),
    ('Z30.0', 'Consejo y asesoramiento general sobre la anticoncepción'),
    ('Z32.1', 'Embarazo confirmado'),
    ('Z34.9', 'Supervisión de embarazo normal no especificado'),
    ('Z51.8', 'Otras atenciones médicas especificadas'),
    ('Z71.3', 'Consulta para instrucción y vigilancia de la dieta'),
    ('Z76.0', 'Consulta para repetición de receta')
) AS v(code, description)
ON CONFLICT (code) DO NOTHING;
--rollback DELETE FROM salud_cie10;
