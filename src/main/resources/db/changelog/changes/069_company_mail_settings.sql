--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Servidor de correo saliente por empresa.
--
-- Tabla propia y no columnas en `companies` a propósito: la fila de la
-- empresa se carga en la sesión y se serializa a la interfaz, así que una
-- contraseña ahí viajaría al navegador en cuanto alguien devolviera la
-- entidad completa. Aquí solo la lee el servicio de correo y su endpoint,
-- que nunca la expone.
--
-- Tampoco va en company_settings, que es clave/valor plano y cuyo GET
-- devuelve TODOS los ajustes con su valor: la credencial quedaría a la
-- vista de cualquiera con sesión.
--
-- La contraseña se guarda en claro por ahora. Cifrarla en reposo queda
-- pendiente y hace falta una clave del servidor para hacerlo bien; lo que
-- esta tabla sí garantiza es que no salga por la API.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:069-company-mail-settings
--comment: Configuración SMTP por empresa, uno a uno.
CREATE TABLE company_mail_settings (
    company_id  BIGINT       PRIMARY KEY REFERENCES companies (id) ON DELETE CASCADE,
    host        VARCHAR(200),
    port        INTEGER      NOT NULL DEFAULT 587,
    username    VARCHAR(200),
    password    VARCHAR(500),
    from_email  VARCHAR(200),
    from_name   VARCHAR(200),
    -- none = sin cifrado (solo para un relay interno), starttls = 587,
    -- ssl = 465. Es el dato que más se equivoca al configurar un SMTP.
    security    VARCHAR(10)  NOT NULL DEFAULT 'starttls',
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_mail_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_mail_security CHECK (security IN ('none', 'starttls', 'ssl'))
);
COMMENT ON COLUMN company_mail_settings.password IS
    'Credencial SMTP. No se devuelve por la API: el endpoint expone solo '
    'hasPassword. Pendiente cifrarla en reposo.';
--rollback DROP TABLE company_mail_settings;

--changeset erp_maya:069-migrate-mail-from
--comment: El remitente ya se guardaba suelto en company_settings; se muda.
INSERT INTO company_mail_settings (company_id, from_email)
SELECT s.company_id, s.setting_value
  FROM company_settings s
 WHERE s.setting_key = 'mail.from'
   AND s.setting_value IS NOT NULL
   AND length(trim(s.setting_value)) > 0
   AND NOT EXISTS (SELECT 1 FROM company_mail_settings m WHERE m.company_id = s.company_id);
DELETE FROM company_settings WHERE setting_key = 'mail.from';
--rollback DELETE FROM company_mail_settings;
