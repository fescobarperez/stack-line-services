package com.erp_maya.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Precio histórico de una relación producto–proveedor.
 *
 * La tabla existe desde la migración 057 pero nunca tuvo entidad: el costo se
 * pisaba en product_suppliers y el anterior desaparecía. Con esto se puede
 * decir desde cuándo rige un precio y cuánto subió respecto del anterior.
 *
 * Una sola fila por relación tiene valid_until NULL: esa es la vigente.
 */
@Entity
@Table(name = "product_supplier_prices")
public class ProductSupplierPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "product_supplier_id", nullable = false)
    private Long productSupplierId;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    /** NULL = precio vigente. */
    @Column(name = "valid_until")
    private Instant validUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getProductSupplierId() { return productSupplierId; }
    public void setProductSupplierId(Long productSupplierId) { this.productSupplierId = productSupplierId; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
    public Instant getCreatedAt() { return createdAt; }
}
