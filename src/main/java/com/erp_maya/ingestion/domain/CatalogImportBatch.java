package com.erp_maya.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "catalog_import_batches")
public class CatalogImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_website_id")
    private Long supplierWebsiteId;

    @Column(name = "crawl_run_id")
    private Long crawlRunId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_scope", columnDefinition = "jsonb")
    private List<Map<String, Object>> categoryScope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_tree", columnDefinition = "jsonb")
    private List<Map<String, Object>> categoryTree;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(nullable = false)
    private String status = "preview";

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Long getSupplierWebsiteId() { return supplierWebsiteId; }
    public void setSupplierWebsiteId(Long supplierWebsiteId) { this.supplierWebsiteId = supplierWebsiteId; }
    public Long getCrawlRunId() { return crawlRunId; }
    public void setCrawlRunId(Long crawlRunId) { this.crawlRunId = crawlRunId; }
    public List<Map<String, Object>> getCategoryScope() { return categoryScope; }
    public void setCategoryScope(List<Map<String, Object>> categoryScope) { this.categoryScope = categoryScope; }
    public List<Map<String, Object>> getCategoryTree() { return categoryTree; }
    public void setCategoryTree(List<Map<String, Object>> categoryTree) { this.categoryTree = categoryTree; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
}
