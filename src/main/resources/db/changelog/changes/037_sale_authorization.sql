--liquibase formatted sql

--changeset erp_maya:037-sale-authorization
--comment: Qué autorización permitió el descuento manual de la venta. Cierra la traza:
--comment: desde el ticket se llega a quién aprobó, cuándo y con qué nivel.
ALTER TABLE sales ADD COLUMN authorization_id BIGINT REFERENCES authorization_requests (id);
CREATE INDEX ix_sales_authorization ON sales (authorization_id);
--rollback DROP INDEX ix_sales_authorization; ALTER TABLE sales DROP COLUMN authorization_id;
