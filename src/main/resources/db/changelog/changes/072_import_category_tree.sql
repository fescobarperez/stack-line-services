--liquibase formatted sql

--changeset erp_maya:072-import-category-tree
-- Persist the discovered category tree so pending selections can be reopened.
ALTER TABLE catalog_import_batches
    ADD COLUMN category_tree JSONB;
