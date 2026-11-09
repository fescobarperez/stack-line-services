--changeset erp_maya:071-import-category-scope
-- Persist the external category selection that produced each crawl proposal.
ALTER TABLE catalog_import_batches
    ADD COLUMN category_scope JSONB;

ALTER TABLE catalog_import_batches
    DROP CONSTRAINT ck_import_batches_status;

ALTER TABLE catalog_import_batches
    ADD CONSTRAINT ck_import_batches_status
    CHECK (status IN ('preview', 'approved', 'applied', 'rejected', 'category_selection'));
