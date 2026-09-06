package com.erp_maya.project.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un cargo al proyecto. `source` dice de dónde viene y `refId` apunta al
 * documento que lo respalda (factura de compra, movimiento de stock…).
 */
@Entity
@Table(name = "project_costs")
public class ProjectCost {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** purchase · material · labor · other */
    @Column(nullable = false)
    private String source;

    @Column(name = "ref_id")
    private Long refId;

    private String description;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "cost_date", nullable = false)
    private LocalDate costDate = LocalDate.now();

    @Column(name = "created_by")
    private Long createdBy;

    /** La autorización que permitió el cargo, si el sobrecosto la exigió. */
    @Column(name = "authorization_id")
    private Long authorizationId;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getCostDate() { return costDate; }
    public void setCostDate(LocalDate costDate) { this.costDate = costDate; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }

    public Instant getCreatedAt() { return createdAt; }
}
