--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Prospectos: personas que pidieron información o una demo (por ahora,
-- desde el chat de TINO en la página web o WhatsApp de la empresa).
--
-- No son clientes todavía: no tienen NIT ni historial, y la mayoría no
-- llega a comprar. Mezclarlos con `clients` ensuciaría el CRM y el punto
-- de venta; cuando uno se convierte, el asesor lo da de alta como cliente.
--
-- source_ref identifica la conversación de origen: si el visitante corrige
-- su teléfono o agrega algo, TINO actualiza el mismo prospecto en vez de
-- crear otro.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:088-leads
CREATE TABLE leads (
    id            BIGSERIAL     PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    source        VARCHAR(30)   NOT NULL DEFAULT 'manual',
    source_ref    VARCHAR(120),
    name          VARCHAR(150)  NOT NULL,
    company_name  VARCHAR(150),
    phone         VARCHAR(40),
    email         VARCHAR(150),
    interest      VARCHAR(80),
    message       VARCHAR(1000),
    wants_demo    BOOLEAN       NOT NULL DEFAULT FALSE,
    status        VARCHAR(20)   NOT NULL DEFAULT 'nuevo',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_leads_contact CHECK (phone IS NOT NULL OR email IS NOT NULL),
    CONSTRAINT ck_leads_status CHECK (status IN ('nuevo', 'contactado', 'descartado', 'convertido'))
);
CREATE INDEX ix_leads_company_status ON leads (company_id, status, created_at DESC);
CREATE UNIQUE INDEX uq_leads_source_ref ON leads (company_id, source_ref) WHERE source_ref IS NOT NULL;
--rollback DROP TABLE leads;
