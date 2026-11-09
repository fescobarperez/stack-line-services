--liquibase formatted sql

-- ══════════════════════════════════════════════════════════════════════
-- Ingestión de catálogos de proveedores · crawling, CSV y MCP.
-- El producto canónico vive en products; estas tablas conservan el
-- registro original del proveedor y el flujo de autorización.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:070-supplier-websites
--comment: Sitios web configurables por proveedor.
CREATE TABLE supplier_websites (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    supplier_id     BIGINT       NOT NULL REFERENCES suppliers (id),
    name            VARCHAR(200) NOT NULL,
    base_url        VARCHAR(1000) NOT NULL,
    website_type    VARCHAR(30)  NOT NULL DEFAULT 'catalog',
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    crawl_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_supplier_websites_type CHECK (website_type IN ('catalog', 'ecommerce', 'technical', 'portal')),
    CONSTRAINT ck_supplier_websites_status CHECK (status IN ('active', 'paused', 'archived'))
);
CREATE INDEX ix_supplier_websites_company ON supplier_websites (company_id, supplier_id, status);

--changeset erp_maya:070-supplier-catalog-items
--comment: Observaciones del catálogo externo, separadas del producto canónico.
CREATE TABLE supplier_catalog_items (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id                  BIGINT        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    supplier_id                 BIGINT        NOT NULL REFERENCES suppliers (id),
    supplier_website_id         BIGINT        NOT NULL REFERENCES supplier_websites (id) ON DELETE CASCADE,
    product_id                  BIGINT        REFERENCES products (id) ON DELETE SET NULL,
    source_product_name         VARCHAR(1000) NOT NULL,
    source_category_path        JSONB,
    source_listing_url          VARCHAR(2000),
    product_url                 VARCHAR(2000),
    supplier_sku                VARCHAR(300),
    brand                       VARCHAR(300),
    manufacturer_name           VARCHAR(300),
    manufacturer_part_number    VARCHAR(300),
    gtin                        VARCHAR(100),
    source_attributes           JSONB,
    observed_unit               VARCHAR(100),
    observed_pack_size         NUMERIC(16,4),
    observed_currency           VARCHAR(3)    NOT NULL DEFAULT 'GTQ',
    observed_unit_cost          NUMERIC(16,4),
    first_seen_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_seen_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    active                      BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_supplier_catalog_pack CHECK (observed_pack_size IS NULL OR observed_pack_size > 0),
    CONSTRAINT ck_supplier_catalog_cost CHECK (observed_unit_cost IS NULL OR observed_unit_cost >= 0)
);
CREATE INDEX ix_supplier_catalog_items_company_product ON supplier_catalog_items (company_id, product_id);
CREATE INDEX ix_supplier_catalog_items_supplier ON supplier_catalog_items (company_id, supplier_id, supplier_sku);
CREATE INDEX ix_supplier_catalog_items_url ON supplier_catalog_items (company_id, supplier_id, product_url);
CREATE INDEX ix_supplier_catalog_items_source_name ON supplier_catalog_items USING gin (to_tsvector('simple', source_product_name));

--changeset erp_maya:070-crawl-runs
--comment: Ejecución y métricas de completitud del crawler.
CREATE TABLE crawl_runs (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id               BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    supplier_website_id      BIGINT       NOT NULL REFERENCES supplier_websites (id),
    user_prompt              TEXT,
    sop_version              VARCHAR(100) NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'queued',
    quality_status            VARCHAR(20)  NOT NULL DEFAULT 'partial',
    pages_discovered         INTEGER      NOT NULL DEFAULT 0,
    pages_visited            INTEGER      NOT NULL DEFAULT 0,
    pages_failed             INTEGER      NOT NULL DEFAULT 0,
    products_extracted       INTEGER      NOT NULL DEFAULT 0,
    quality_report           JSONB,
    suggested_sop_changes    JSONB,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ,
    CONSTRAINT ck_crawl_runs_status CHECK (status IN ('queued', 'running', 'completed', 'failed', 'cancelled')),
    CONSTRAINT ck_crawl_runs_quality CHECK (quality_status IN ('complete', 'incomplete', 'partial', 'failed'))
);
CREATE INDEX ix_crawl_runs_company_site ON crawl_runs (company_id, supplier_website_id, created_at DESC);

--changeset erp_maya:070-import-batches
--comment: Lote común para CSV, crawling y MCP; siempre inicia en preview.
CREATE TABLE catalog_import_batches (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    supplier_id           BIGINT       NOT NULL REFERENCES suppliers (id),
    supplier_website_id   BIGINT       REFERENCES supplier_websites (id),
    crawl_run_id          BIGINT       REFERENCES crawl_runs (id),
    source_type           VARCHAR(20)  NOT NULL,
    source_reference      VARCHAR(1000),
    status                VARCHAR(20)  NOT NULL DEFAULT 'preview',
    created_by            VARCHAR(200),
    approved_by           VARCHAR(200),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_at           TIMESTAMPTZ,
    applied_at            TIMESTAMPTZ,
    CONSTRAINT ck_import_batches_source CHECK (source_type IN ('csv', 'crawl', 'mcp')),
    CONSTRAINT ck_import_batches_status CHECK (status IN ('preview', 'approved', 'applied', 'rejected'))
);
CREATE INDEX ix_import_batches_company ON catalog_import_batches (company_id, status, created_at DESC);

--changeset erp_maya:070-import-items
--comment: Decisión y evidencia por fila, incluyendo las filas OMIT.
CREATE TABLE catalog_import_items (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id                 BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    batch_id                   BIGINT       NOT NULL REFERENCES catalog_import_batches (id) ON DELETE CASCADE,
    row_number                 INTEGER      NOT NULL,
    raw_payload                JSONB        NOT NULL,
    normalized_payload         JSONB,
    action                     VARCHAR(30)  NOT NULL DEFAULT 'REVIEW',
    target_product_id          BIGINT       REFERENCES products (id),
    target_product_supplier_id BIGINT       REFERENCES product_suppliers (id),
    confidence                 JSONB,
    decision_reason             TEXT,
    evidence                   JSONB,
    alternative_candidates     JSONB,
    status                     VARCHAR(20)  NOT NULL DEFAULT 'pending',
    error_message              TEXT,
    CONSTRAINT ck_import_items_action CHECK (action IN ('CREATE', 'LINK_EXISTING_PRODUCT', 'UPDATE_OFFER', 'UPDATE_PRODUCT', 'REVIEW', 'OMIT')),
    CONSTRAINT ck_import_items_status CHECK (status IN ('pending', 'selected', 'rejected', 'applied', 'failed')),
    CONSTRAINT uq_import_items_row UNIQUE (batch_id, row_number)
);
CREATE INDEX ix_import_items_company_batch ON catalog_import_items (company_id, batch_id, status);
CREATE INDEX ix_import_items_target ON catalog_import_items (company_id, target_product_id);

--changeset erp_maya:070-search-documents splitStatements:false
--comment: Proyección para búsqueda híbrida; usa pgvector si el servidor lo ofrece y JSONB como fallback local.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
        CREATE TABLE product_search_documents (
            id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            company_id            BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
            product_id            BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
            search_text           TEXT         NOT NULL,
            normalized_attributes JSONB,
            embedding             vector(1536),
            embedding_provider    VARCHAR(100),
            embedding_model      VARCHAR(200),
            embedding_version     VARCHAR(100),
            content_hash          VARCHAR(128),
            indexed_at            TIMESTAMPTZ,
            updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
            CONSTRAINT uq_product_search_documents_product UNIQUE (company_id, product_id)
        );
    ELSE
        CREATE TABLE product_search_documents (
            id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            company_id            BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
            product_id            BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
            search_text           TEXT         NOT NULL,
            normalized_attributes JSONB,
            embedding             JSONB,
            embedding_provider    VARCHAR(100),
            embedding_model      VARCHAR(200),
            embedding_version     VARCHAR(100),
            content_hash          VARCHAR(128),
            indexed_at            TIMESTAMPTZ,
            updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
            CONSTRAINT uq_product_search_documents_product UNIQUE (company_id, product_id)
        );
    END IF;
END $$;
CREATE INDEX ix_product_search_documents_company ON product_search_documents (company_id, product_id);
CREATE INDEX ix_product_search_documents_text ON product_search_documents USING gin (to_tsvector('simple', search_text));
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE INDEX ix_product_search_documents_embedding ON product_search_documents USING hnsw (embedding vector_cosine_ops);
    END IF;
END $$;

--rollback DROP TABLE product_search_documents;
--rollback DROP TABLE catalog_import_items;
--rollback DROP TABLE catalog_import_batches;
--rollback DROP TABLE crawl_runs;
--rollback DROP TABLE supplier_catalog_items;
--rollback DROP TABLE supplier_websites;
