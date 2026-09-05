package com.erp_maya.asset.domain;

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
import java.time.LocalDate;

/** Cuota de depreciación de un activo en un período. */
@Entity
@Table(name = "asset_depreciation")
public class AssetDepreciation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixed_asset_id", nullable = false)
    private FixedAsset fixedAsset;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "depreciation_amount", nullable = false)
    private BigDecimal depreciationAmount;

    @Column(name = "accumulated_amount")
    private BigDecimal accumulatedAmount;

    @Column(name = "book_value")
    private BigDecimal bookValue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public FixedAsset getFixedAsset() { return fixedAsset; }
    public void setFixedAsset(FixedAsset fixedAsset) { this.fixedAsset = fixedAsset; }

    public LocalDate getPeriodDate() { return periodDate; }
    public void setPeriodDate(LocalDate periodDate) { this.periodDate = periodDate; }

    public BigDecimal getDepreciationAmount() { return depreciationAmount; }
    public void setDepreciationAmount(BigDecimal depreciationAmount) { this.depreciationAmount = depreciationAmount; }

    public BigDecimal getAccumulatedAmount() { return accumulatedAmount; }
    public void setAccumulatedAmount(BigDecimal accumulatedAmount) { this.accumulatedAmount = accumulatedAmount; }

    public BigDecimal getBookValue() { return bookValue; }
    public void setBookValue(BigDecimal bookValue) { this.bookValue = bookValue; }
}
