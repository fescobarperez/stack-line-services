package com.erp_maya.uom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Factor de conversión entre dos unidades (p.ej. 1 caja = 12 unidades). */
@Entity
@Table(name = "uom_conversions")
public class UomConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_uom_id", nullable = false)
    private UnitOfMeasure fromUom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_uom_id", nullable = false)
    private UnitOfMeasure toUom;

    @Column(nullable = false)
    private BigDecimal factor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public UnitOfMeasure getFromUom() { return fromUom; }
    public void setFromUom(UnitOfMeasure fromUom) { this.fromUom = fromUom; }

    public UnitOfMeasure getToUom() { return toUom; }
    public void setToUom(UnitOfMeasure toUom) { this.toUom = toUom; }

    public BigDecimal getFactor() { return factor; }
    public void setFactor(BigDecimal factor) { this.factor = factor; }
}
