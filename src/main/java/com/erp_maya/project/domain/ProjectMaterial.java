package com.erp_maya.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Material de catálogo planificado para un proyecto. */
@Entity
@Table(name = "project_materials")
public class ProjectMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "group_id")
    private Long groupId;

    /** Cotización a la que este material fue asignado. NULL = disponible en el pool. */
    @Column(name = "quote_id")
    private Long quoteId;

    /** Línea de cotización concreta que agrupa este material. */
    @Column(name = "quote_item_id")
    private Long quoteItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "quantity_planned", nullable = false)
    private BigDecimal quantityPlanned;

    @Column(nullable = false)
    private String uom = "unid";

    @Column(name = "unit_cost_snapshot", nullable = false)
    private BigDecimal unitCostSnapshot = BigDecimal.ZERO;

    @Column(name = "cost_snapshot_at", nullable = false)
    private Instant costSnapshotAt;

    @Column(name = "quantity_ordered", nullable = false)
    private BigDecimal quantityOrdered = BigDecimal.ZERO;

    @Column(name = "quantity_consumed", nullable = false)
    private BigDecimal quantityConsumed = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "planned";

    private String notes;

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
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public Long getQuoteItemId() { return quoteItemId; }
    public void setQuoteItemId(Long quoteItemId) { this.quoteItemId = quoteItemId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public BigDecimal getQuantityPlanned() { return quantityPlanned; }
    public void setQuantityPlanned(BigDecimal quantityPlanned) { this.quantityPlanned = quantityPlanned; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public BigDecimal getUnitCostSnapshot() { return unitCostSnapshot; }
    public void setUnitCostSnapshot(BigDecimal unitCostSnapshot) { this.unitCostSnapshot = unitCostSnapshot; }
    public Instant getCostSnapshotAt() { return costSnapshotAt; }
    public void setCostSnapshotAt(Instant costSnapshotAt) { this.costSnapshotAt = costSnapshotAt; }
    public BigDecimal getQuantityOrdered() { return quantityOrdered; }
    public void setQuantityOrdered(BigDecimal quantityOrdered) { this.quantityOrdered = quantityOrdered; }
    public BigDecimal getQuantityConsumed() { return quantityConsumed; }
    public void setQuantityConsumed(BigDecimal quantityConsumed) { this.quantityConsumed = quantityConsumed; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
