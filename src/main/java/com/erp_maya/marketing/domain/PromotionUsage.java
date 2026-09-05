package com.erp_maya.marketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Registro de una aplicación de promoción (para métricas de efectividad). */
@Entity
@Table(name = "promotion_usage")
public class PromotionUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Column(name = "sale_id")
    private Long saleId;

    @Column(name = "amount_saved", nullable = false)
    private BigDecimal amountSaved = BigDecimal.ZERO;

    private String reference;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public PromotionUsage() {}

    public PromotionUsage(Long companyId, Long promotionId, Long saleId, BigDecimal amountSaved, String reference) {
        this.companyId = companyId;
        this.promotionId = promotionId;
        this.saleId = saleId;
        this.amountSaved = amountSaved != null ? amountSaved : BigDecimal.ZERO;
        this.reference = reference;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getPromotionId() { return promotionId; }
    public void setPromotionId(Long promotionId) { this.promotionId = promotionId; }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public BigDecimal getAmountSaved() { return amountSaved; }
    public void setAmountSaved(BigDecimal amountSaved) { this.amountSaved = amountSaved; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}
