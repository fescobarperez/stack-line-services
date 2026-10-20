package com.erp_maya.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Foto del producto (completa + miniatura). Ver 087_product_images.sql. */
@Entity
@Table(name = "product_images")
public class ProductImage {

    /** Mismo id que el producto: hay una sola foto por producto. */
    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "image_mime", nullable = false)
    private String imageMime;

    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    @Column(name = "thumb_mime", nullable = false)
    private String thumbMime;

    @Column(name = "thumb_data", nullable = false)
    private byte[] thumbData;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getImageMime() { return imageMime; }
    public void setImageMime(String imageMime) { this.imageMime = imageMime; }
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
    public String getThumbMime() { return thumbMime; }
    public void setThumbMime(String thumbMime) { this.thumbMime = thumbMime; }
    public byte[] getThumbData() { return thumbData; }
    public void setThumbData(byte[] thumbData) { this.thumbData = thumbData; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
