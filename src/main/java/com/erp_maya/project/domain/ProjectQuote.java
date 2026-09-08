package com.erp_maya.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Asociación auditable: un proyecto puede agregar múltiples cotizaciones cliente. */
@Entity
@Table(name = "project_quotes")
public class ProjectQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Column(name = "amount_snapshot", nullable = false)
    private BigDecimal amountSnapshot = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean included = Boolean.FALSE;

    @Column(name = "included_at")
    private Instant includedAt;

    @Column(name = "excluded_at")
    private Instant excludedAt;

    @Column(name = "exclusion_reason")
    private String exclusionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public BigDecimal getAmountSnapshot() { return amountSnapshot; }
    public void setAmountSnapshot(BigDecimal amountSnapshot) { this.amountSnapshot = amountSnapshot; }
    public Boolean getIncluded() { return included; }
    public void setIncluded(Boolean included) { this.included = included; }
    public Instant getIncludedAt() { return includedAt; }
    public void setIncludedAt(Instant includedAt) { this.includedAt = includedAt; }
    public Instant getExcludedAt() { return excludedAt; }
    public void setExcludedAt(Instant excludedAt) { this.excludedAt = excludedAt; }
    public String getExclusionReason() { return exclusionReason; }
    public void setExclusionReason(String exclusionReason) { this.exclusionReason = exclusionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
