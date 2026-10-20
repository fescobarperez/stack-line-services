--liquibase formatted sql
-- ══════════════════════════════════════════════════════════════════════
-- Foto del producto. Una por producto, en su propia tabla para que los
-- listados de productos no carguen los bytes.
--
-- El navegador la reduce antes de subirla: image_data (~1000 px) para el
-- detalle y thumb_data (~240 px) para el punto de venta y los listados.
-- products.image_updated_at es la versión: viaja en ProductResponse como
-- imageVersion y el front la usa para pedir (y cachear) la imagen correcta
-- sin una consulta por producto.
-- ══════════════════════════════════════════════════════════════════════

--changeset erp_maya:087-product-images
CREATE TABLE product_images (
    product_id   BIGINT       PRIMARY KEY REFERENCES products (id) ON DELETE CASCADE,
    company_id   BIGINT       NOT NULL REFERENCES companies (id),
    image_mime   VARCHAR(40)  NOT NULL,
    image_data   BYTEA        NOT NULL,
    thumb_mime   VARCHAR(40)  NOT NULL,
    thumb_data   BYTEA        NOT NULL,
    updated_by   VARCHAR(120),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_product_images_company ON product_images (company_id);
ALTER TABLE products ADD COLUMN image_updated_at TIMESTAMPTZ;
--rollback ALTER TABLE products DROP COLUMN image_updated_at;
--rollback DROP TABLE product_images;
