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
import java.util.Map;

@Entity
@Table(name = "crawl_runs")
public class CrawlRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "supplier_website_id", nullable = false) private Long supplierWebsiteId;
    @Column(name = "user_prompt") private String userPrompt;
    @Column(name = "sop_version", nullable = false) private String sopVersion;
    @Column(nullable = false) private String status = "queued";
    @Column(name = "quality_status", nullable = false) private String qualityStatus = "partial";
    @Column(name = "pages_discovered", nullable = false) private Integer pagesDiscovered = 0;
    @Column(name = "pages_visited", nullable = false) private Integer pagesVisited = 0;
    @Column(name = "pages_failed", nullable = false) private Integer pagesFailed = 0;
    @Column(name = "products_extracted", nullable = false) private Integer productsExtracted = 0;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "quality_report", columnDefinition = "jsonb") private Map<String, Object> qualityReport;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "suggested_sop_changes", columnDefinition = "jsonb") private Map<String, Object> suggestedSopChanges;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getSupplierWebsiteId() { return supplierWebsiteId; }
    public void setSupplierWebsiteId(Long supplierWebsiteId) { this.supplierWebsiteId = supplierWebsiteId; }
    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }
    public String getSopVersion() { return sopVersion; }
    public void setSopVersion(String sopVersion) { this.sopVersion = sopVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getQualityStatus() { return qualityStatus; }
    public void setQualityStatus(String qualityStatus) { this.qualityStatus = qualityStatus; }
    public Integer getPagesDiscovered() { return pagesDiscovered; }
    public void setPagesDiscovered(Integer pagesDiscovered) { this.pagesDiscovered = pagesDiscovered; }
    public Integer getPagesVisited() { return pagesVisited; }
    public void setPagesVisited(Integer pagesVisited) { this.pagesVisited = pagesVisited; }
    public Integer getPagesFailed() { return pagesFailed; }
    public void setPagesFailed(Integer pagesFailed) { this.pagesFailed = pagesFailed; }
    public Integer getProductsExtracted() { return productsExtracted; }
    public void setProductsExtracted(Integer productsExtracted) { this.productsExtracted = productsExtracted; }
    public Map<String, Object> getQualityReport() { return qualityReport; }
    public void setQualityReport(Map<String, Object> qualityReport) { this.qualityReport = qualityReport; }
    public Map<String, Object> getSuggestedSopChanges() { return suggestedSopChanges; }
    public void setSuggestedSopChanges(Map<String, Object> suggestedSopChanges) { this.suggestedSopChanges = suggestedSopChanges; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
