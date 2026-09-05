--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Catálogos maestros. Todos aislados por company_id.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:002-roles
--comment: Roles de seguridad; los permisos se definen una vez por rol y se reutilizan.
CREATE TABLE roles (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES companies (id),
    name         VARCHAR(80)  NOT NULL,
    description  TEXT,
    permissions  JSONB        NOT NULL DEFAULT '[]',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_name UNIQUE (company_id, name)
);
CREATE INDEX ix_roles_company ON roles (company_id);
--rollback DROP TABLE roles;

--changeset erp_maya:002-branches
--comment: Sucursales operativas. Facturan bajo un establecimiento SAT (establishment_id).
CREATE TABLE branches (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id        BIGINT       NOT NULL REFERENCES companies (id),
    establishment_id  BIGINT       REFERENCES establishments (id),
    name              VARCHAR(160) NOT NULL,
    address           TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_branches_company ON branches (company_id);
CREATE INDEX ix_branches_establishment ON branches (establishment_id);
--rollback DROP TABLE branches;

--changeset erp_maya:002-users
--comment: Usuarios/operadores. Cada uno pertenece a una empresa; da trazabilidad de operaciones.
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT       NOT NULL REFERENCES companies (id),
    role_id        BIGINT       REFERENCES roles (id),
    branch_id      BIGINT       REFERENCES branches (id),
    name           VARCHAR(160) NOT NULL,
    email          VARCHAR(160),
    password_hash  VARCHAR(200),
    status         VARCHAR(20)  NOT NULL DEFAULT 'active',
    last_seen_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (company_id, email)
);
CREATE INDEX ix_users_company ON users (company_id);
--rollback DROP TABLE users;

--changeset erp_maya:002-categories
--comment: Categorías de producto para segmentar catálogo, reportar por rubro y promos.
CREATE TABLE categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    name        VARCHAR(120) NOT NULL,
    icon        VARCHAR(16),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_name UNIQUE (company_id, name)
);
CREATE INDEX ix_categories_company ON categories (company_id);
--rollback DROP TABLE categories;

--changeset erp_maya:002-products
--comment: Maestro central de artículos. SKU único por empresa; el stock va aparte (por sucursal).
CREATE TABLE products (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id),
    category_id  BIGINT        REFERENCES categories (id),
    sku          VARCHAR(64)   NOT NULL,
    name         VARCHAR(200)  NOT NULL,
    price        NUMERIC(14,2) NOT NULL DEFAULT 0,
    cost         NUMERIC(14,4) NOT NULL DEFAULT 0,
    avg_cost     NUMERIC(14,4),
    unit         VARCHAR(20)   NOT NULL DEFAULT 'unid',
    min_stock    NUMERIC(14,3) NOT NULL DEFAULT 0,
    status       VARCHAR(20)   NOT NULL DEFAULT 'active',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_sku UNIQUE (company_id, sku)
);
CREATE INDEX ix_products_company ON products (company_id);
CREATE INDEX ix_products_category ON products (category_id);
--rollback DROP TABLE products;

--changeset erp_maya:002-suppliers
--comment: Maestro de proveedores para Compras y Cuentas por Pagar.
CREATE TABLE suppliers (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    name           VARCHAR(200)  NOT NULL,
    nit            VARCHAR(20),
    contact        VARCHAR(160),
    phone          VARCHAR(40),
    payment_terms  VARCHAR(40),
    balance        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'active',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_suppliers_company ON suppliers (company_id);
--rollback DROP TABLE suppliers;

--changeset erp_maya:002-clients
--comment: Maestro de clientes (a quienes vende la empresa). client_type rige impuestos/precios.
CREATE TABLE clients (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    name           VARCHAR(200)  NOT NULL,
    nit            VARCHAR(20),
    client_type    VARCHAR(20)   NOT NULL DEFAULT 'CF',
    address        TEXT,
    phone          VARCHAR(40),
    email          VARCHAR(160),
    credit_limit   NUMERIC(14,2) NOT NULL DEFAULT 0,
    payment_terms  INTEGER       NOT NULL DEFAULT 0,
    balance        NUMERIC(14,2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'active',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_clients_company ON clients (company_id);
--rollback DROP TABLE clients;
