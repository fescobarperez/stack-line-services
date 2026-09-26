package com.erp_maya.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Sitio web de catálogo, tienda o ficha técnica de un proveedor. */
@Entity
@Table(name = "supplier_websites")
public class SupplierWebsite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false, length = 1000)
    private String baseUrl;

    @Column(name = "website_type", nullable = false)
    private String websiteType = "catalog";

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "crawl_enabled", nullable = false)
    private Boolean crawlEnabled = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getWebsiteType() { return websiteType; }
    public void setWebsiteType(String websiteType) { this.websiteType = websiteType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getCrawlEnabled() { return crawlEnabled; }
    public void setCrawlEnabled(Boolean crawlEnabled) { this.crawlEnabled = crawlEnabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
