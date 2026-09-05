--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Planilla (Payroll). Empleados, períodos de nómina y su detalle por empleado.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:020-employees
--comment: Empleados de la empresa (distintos de los users del sistema). Datos laborales y de pago (DPI, NIT, banco).
CREATE TABLE employees (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    employee_code  VARCHAR(20)   NOT NULL,
    name           VARCHAR(160)  NOT NULL,
    department     VARCHAR(60),
    position       VARCHAR(80),
    salary         NUMERIC(14,2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'active',
    hired_date     DATE,
    dpi            VARCHAR(20),
    nit            VARCHAR(20),
    bank_name      VARCHAR(60),
    bank_account   VARCHAR(40),
    branch_id      BIGINT        REFERENCES branches (id),
    user_id        BIGINT        REFERENCES users (id),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_employees_code UNIQUE (company_id, employee_code)
);
CREATE INDEX ix_employees_company ON employees (company_id);
--rollback DROP TABLE employees;

--changeset erp_maya:020-payroll_periods
--comment: Corrida de nómina de un mes. status cerrada bloquea cambios; total/employee_count son el resumen.
CREATE TABLE payroll_periods (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT        NOT NULL REFERENCES companies (id),
    period_code     VARCHAR(20)   NOT NULL,
    name            VARCHAR(60),
    month           INTEGER,
    year            INTEGER,
    status          VARCHAR(20)   NOT NULL DEFAULT 'open',
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    employee_count  INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_periods_code UNIQUE (company_id, period_code)
);
CREATE INDEX ix_payroll_periods_company ON payroll_periods (company_id);
--rollback DROP TABLE payroll_periods;

--changeset erp_maya:020-payroll_entries
--comment: Renglón de nómina por empleado: salario base, bonos y deducciones (IGSS/ISR) → sueldo líquido.
CREATE TABLE payroll_entries (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL REFERENCES companies (id),
    payroll_period_id  BIGINT        NOT NULL REFERENCES payroll_periods (id) ON DELETE CASCADE,
    employee_id        BIGINT        NOT NULL REFERENCES employees (id),
    base_salary        NUMERIC(14,2) NOT NULL DEFAULT 0,
    bonuses            NUMERIC(14,2) NOT NULL DEFAULT 0,
    igss_deduction     NUMERIC(14,2) NOT NULL DEFAULT 0,
    isr_deduction      NUMERIC(14,2) NOT NULL DEFAULT 0,
    other_deductions   NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_pay            NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_payroll_entries_period ON payroll_entries (payroll_period_id);
CREATE INDEX ix_payroll_entries_employee ON payroll_entries (employee_id);
--rollback DROP TABLE payroll_entries;
