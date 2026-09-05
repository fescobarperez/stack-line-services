--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Lealtad. Cuentas de puntos de clientes y su historial de movimientos.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:022-loyalty_accounts
--comment: Cuenta de puntos de un cliente. points_balance es el saldo vigente; tier deriva del total acumulado.
CREATE TABLE loyalty_accounts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id          BIGINT        NOT NULL REFERENCES companies (id),
    client_id           BIGINT        REFERENCES clients (id),
    member_code         VARCHAR(20)   NOT NULL,
    name                VARCHAR(160),
    nit                 VARCHAR(20),
    phone               VARCHAR(40),
    email               VARCHAR(160),
    points_balance      INTEGER       NOT NULL DEFAULT 0,
    total_spent         NUMERIC(14,2) NOT NULL DEFAULT 0,
    tier                VARCHAR(30),
    join_date           DATE,
    last_purchase_date  DATE,
    points_earned       INTEGER       NOT NULL DEFAULT 0,
    points_redeemed     INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_loyalty_member UNIQUE (company_id, member_code)
);
CREATE INDEX ix_loyalty_accounts_company ON loyalty_accounts (company_id);
CREATE INDEX ix_loyalty_accounts_client ON loyalty_accounts (client_id);
--rollback DROP TABLE loyalty_accounts;

--changeset erp_maya:022-loyalty_movements
--comment: Movimiento de puntos (earned/redeemed/bonus). Enlaza la venta origen; points con signo (+/−).
CREATE TABLE loyalty_movements (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id          BIGINT        NOT NULL REFERENCES companies (id),
    loyalty_account_id  BIGINT        NOT NULL REFERENCES loyalty_accounts (id) ON DELETE CASCADE,
    movement_type       VARCHAR(20)   NOT NULL,
    points              INTEGER       NOT NULL,
    sale_id             BIGINT        REFERENCES sales (id),
    reference           VARCHAR(60),
    amount              NUMERIC(14,2) NOT NULL DEFAULT 0,
    movement_date       DATE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_loyalty_movements_account ON loyalty_movements (loyalty_account_id);
CREATE INDEX ix_loyalty_movements_sale ON loyalty_movements (sale_id);
--rollback DROP TABLE loyalty_movements;
