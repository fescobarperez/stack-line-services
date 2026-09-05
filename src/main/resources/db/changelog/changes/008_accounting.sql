--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Contabilidad. Plan de cuentas jerárquico, períodos, pólizas y partidas.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:008-accounts
--comment: Plan de cuentas jerárquico (parent_id auto-referencia). Solo hojas (allows_entries) reciben movimientos.
CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES companies (id),
    parent_id       BIGINT       REFERENCES accounts (id),
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    level           INTEGER,
    normal_balance  VARCHAR(6)   NOT NULL,
    allows_entries  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_accounts_code UNIQUE (company_id, code),
    CONSTRAINT ck_accounts_balance CHECK (normal_balance IN ('debit', 'credit'))
);
CREATE INDEX ix_accounts_company ON accounts (company_id);
CREATE INDEX ix_accounts_parent ON accounts (parent_id);
--rollback DROP TABLE accounts;

--changeset erp_maya:008-accounting_periods
--comment: Períodos contables. Un período 'closed' bloquea nuevas pólizas en esa fecha.
CREATE TABLE accounting_periods (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    name        VARCHAR(60)  NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'open',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_periods_name UNIQUE (company_id, name)
);
CREATE INDEX ix_periods_company ON accounting_periods (company_id);
--rollback DROP TABLE accounting_periods;

--changeset erp_maya:008-journal_entries
--comment: Encabezado de póliza. source_type enlaza al documento origen para generación automática.
CREATE TABLE journal_entries (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    period_id     BIGINT        REFERENCES accounting_periods (id),
    entry_date    DATE          NOT NULL,
    entry_type    VARCHAR(20),
    description   TEXT,
    reference     VARCHAR(60),
    source_type   VARCHAR(30),
    total_debit   NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_credit  NUMERIC(14,2) NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL DEFAULT 'draft',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_journal_entries_company ON journal_entries (company_id);
CREATE INDEX ix_journal_entries_period ON journal_entries (period_id);
--rollback DROP TABLE journal_entries;

--changeset erp_maya:008-journal_entry_lines
--comment: Partidas (corazón de la partida doble). Cada línea afecta una cuenta al debe o al haber.
CREATE TABLE journal_entry_lines (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    entry_id     BIGINT        NOT NULL REFERENCES journal_entries (id) ON DELETE CASCADE,
    account_id   BIGINT        NOT NULL REFERENCES accounts (id),
    debit        NUMERIC(14,2) NOT NULL DEFAULT 0,
    credit       NUMERIC(14,2) NOT NULL DEFAULT 0
);
CREATE INDEX ix_journal_lines_entry ON journal_entry_lines (entry_id);
CREATE INDEX ix_journal_lines_account ON journal_entry_lines (account_id);
CREATE INDEX ix_journal_lines_company ON journal_entry_lines (company_id);
--rollback DROP TABLE journal_entry_lines;
