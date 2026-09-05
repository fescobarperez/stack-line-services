--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Bancos y conciliación. Cuentas bancarias, sus movimientos y conciliaciones.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:023-bank_accounts
--comment: Cuenta bancaria operativa. gl_account_id la enlaza a la cuenta contable (accounts) para conciliar.
CREATE TABLE bank_accounts (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id           BIGINT        NOT NULL REFERENCES companies (id),
    account_code         VARCHAR(20)   NOT NULL,
    bank_name            VARCHAR(80),
    account_type         VARCHAR(20),
    currency             VARCHAR(3)    NOT NULL DEFAULT 'GTQ',
    account_number       VARCHAR(40),
    alias                VARCHAR(160),
    balance              NUMERIC(16,2) NOT NULL DEFAULT 0,
    book_balance         NUMERIC(16,2) NOT NULL DEFAULT 0,
    status               VARCHAR(20)   NOT NULL DEFAULT 'active',
    opened_date          DATE,
    last_movement_date   DATE,
    gl_account_id        BIGINT        REFERENCES accounts (id),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_bank_accounts_code UNIQUE (company_id, account_code)
);
CREATE INDEX ix_bank_accounts_company ON bank_accounts (company_id);
CREATE INDEX ix_bank_accounts_gl ON bank_accounts (gl_account_id);
--rollback DROP TABLE bank_accounts;

--changeset erp_maya:023-bank_movements
--comment: Movimiento bancario (depósito/retiro/transferencia/débito). reconciled marca si ya se concilió.
CREATE TABLE bank_movements (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT        NOT NULL REFERENCES companies (id),
    bank_account_id  BIGINT        NOT NULL REFERENCES bank_accounts (id) ON DELETE CASCADE,
    movement_date    DATE,
    description      TEXT,
    reference        VARCHAR(80),
    movement_type    VARCHAR(20),
    amount           NUMERIC(16,2) NOT NULL,
    running_balance  NUMERIC(16,2),
    reconciled       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_bank_movements_account ON bank_movements (bank_account_id);
CREATE INDEX ix_bank_movements_date ON bank_movements (movement_date);
--rollback DROP TABLE bank_movements;

--changeset erp_maya:023-bank_reconciliations
--comment: Sesión de conciliación: compara saldo contable (book) vs saldo del banco a una fecha de estado.
CREATE TABLE bank_reconciliations (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT        NOT NULL REFERENCES companies (id),
    bank_account_id  BIGINT        NOT NULL REFERENCES bank_accounts (id),
    statement_date   DATE          NOT NULL,
    book_balance     NUMERIC(16,2),
    bank_balance     NUMERIC(16,2),
    difference       NUMERIC(16,2),
    status           VARCHAR(20)   NOT NULL DEFAULT 'open',
    notes            TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_bank_reconciliations_account ON bank_reconciliations (bank_account_id);
--rollback DROP TABLE bank_reconciliations;
