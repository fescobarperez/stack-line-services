--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Capa 1 del módulo de autorizaciones: estructura organizativa y moneda.
--
-- Nada de esto es específico de autorizaciones —jerarquía, alcance por
-- sucursal y moneda base son datos de la empresa—, pero el motor los
-- necesita antes de existir: sin jerarquía no hay a quién escalar, sin
-- alcance no se sabe qué sucursales cubre un aprobador, y sin moneda base
-- un umbral de "hasta 5000" es ambiguo.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:035-currencies
--comment: Catálogo ISO de monedas. Global: los códigos no son por empresa.
CREATE TABLE currencies (
    code       VARCHAR(3)  PRIMARY KEY,
    name       VARCHAR(60) NOT NULL,
    symbol     VARCHAR(6)  NOT NULL,
    decimals   SMALLINT    NOT NULL DEFAULT 2,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO currencies (code, name, symbol, decimals) VALUES
    ('GTQ', 'Quetzal guatemalteco', 'Q',   2),
    ('USD', 'Dólar estadounidense', '$',   2),
    ('EUR', 'Euro',                 '€',   2),
    ('MXN', 'Peso mexicano',        'MX$', 2),
    ('HNL', 'Lempira hondureño',    'L',   2),
    ('CRC', 'Colón costarricense',  '₡',   2);
--rollback DROP TABLE currencies;

--changeset erp_maya:035-company-base-currency
--comment: Moneda base de la empresa. Toda transacción se expresa y consolida aquí.
ALTER TABLE companies ADD COLUMN base_currency VARCHAR(3) NOT NULL DEFAULT 'GTQ'
    REFERENCES currencies (code);
--rollback ALTER TABLE companies DROP COLUMN base_currency;

--changeset erp_maya:035-user-hierarchy
--comment: Jefe directo. El árbol: hojas = usuarios operativos, padres = quienes aprueban.
ALTER TABLE users ADD COLUMN manager_id BIGINT REFERENCES users (id);
CREATE INDEX ix_users_manager ON users (manager_id);
--rollback ALTER TABLE users DROP COLUMN manager_id;

--changeset erp_maya:035-authorization-levels
--comment: Niveles de autoridad, configurables por empresa. `rank` ordena: mayor = más autoridad.
CREATE TABLE authorization_levels (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies (id),
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    rank        SMALLINT     NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_auth_levels_code UNIQUE (company_id, code),
    CONSTRAINT uq_auth_levels_rank UNIQUE (company_id, rank)
);
--rollback DROP TABLE authorization_levels;

--changeset erp_maya:035-authorization-levels-seed
--comment: Juego por defecto para las empresas existentes. Editable desde la UI.
INSERT INTO authorization_levels (company_id, code, name, rank)
SELECT c.id, v.code, v.name, v.rank
FROM companies c
CROSS JOIN (VALUES
    ('operativo',  'Operativo',  0),
    ('supervisor', 'Supervisor', 1),
    ('gerente',    'Gerente',    2),
    ('direccion',  'Dirección',  3)
) AS v(code, name, rank);
--rollback DELETE FROM authorization_levels;

--changeset erp_maya:035-user-auth-level
--comment: Nivel del usuario. NULL = operativo, no aprueba nada.
ALTER TABLE users ADD COLUMN auth_level_id BIGINT REFERENCES authorization_levels (id);
--rollback ALTER TABLE users DROP COLUMN auth_level_id;

--changeset erp_maya:035-user-branches
--comment: Alcance del aprobador. users.branch_id es dónde trabaja; esto es qué cubre.
CREATE TABLE user_branches (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT      NOT NULL REFERENCES companies (id),
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    branch_id   BIGINT      NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_branches UNIQUE (user_id, branch_id)
);
CREATE INDEX ix_user_branches_user   ON user_branches (user_id);
CREATE INDEX ix_user_branches_branch ON user_branches (branch_id);
--rollback DROP TABLE user_branches;

--changeset erp_maya:035-user-branches-seed
--comment: Cada usuario con sucursal asignada arranca cubriendo al menos esa.
INSERT INTO user_branches (company_id, user_id, branch_id)
SELECT u.company_id, u.id, u.branch_id
FROM users u WHERE u.branch_id IS NOT NULL;
--rollback DELETE FROM user_branches;
