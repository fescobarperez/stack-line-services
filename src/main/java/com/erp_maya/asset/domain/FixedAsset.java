package com.erp_maya.asset.domain;

import com.erp_maya.company.domain.Branch;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Activo fijo depreciable. */
@Entity
@Table(name = "fixed_assets")
public class FixedAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "asset_code", nullable = false)
    private String assetCode;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(name = "purchase_cost")
    private BigDecimal purchaseCost = BigDecimal.ZERO;

    @Column(name = "acquired_date")
    private LocalDate acquiredDate;

    private String serial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    private String status = "active";

    @Column(name = "depreciation_rate")
    private BigDecimal depreciationRate;

    @Column(name = "useful_life_years")
    private Integer usefulLifeYears;

    @Column(name = "accumulated_depreciation")
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @Column(name = "book_value")
    private BigDecimal bookValue;

    @Column(name = "disposal_date")
    private LocalDate disposalDate;

    @Column(name = "disposal_journal_entry_id")
    private Long disposalJournalEntryId;

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

    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPurchaseCost() { return purchaseCost; }
    public void setPurchaseCost(BigDecimal purchaseCost) { this.purchaseCost = purchaseCost; }

    public LocalDate getAcquiredDate() { return acquiredDate; }
    public void setAcquiredDate(LocalDate acquiredDate) { this.acquiredDate = acquiredDate; }

    public String getSerial() { return serial; }
    public void setSerial(String serial) { this.serial = serial; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getDepreciationRate() { return depreciationRate; }
    public void setDepreciationRate(BigDecimal depreciationRate) { this.depreciationRate = depreciationRate; }

    public Integer getUsefulLifeYears() { return usefulLifeYears; }
    public void setUsefulLifeYears(Integer usefulLifeYears) { this.usefulLifeYears = usefulLifeYears; }

    public BigDecimal getAccumulatedDepreciation() { return accumulatedDepreciation; }
    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) { this.accumulatedDepreciation = accumulatedDepreciation; }

    public BigDecimal getBookValue() { return bookValue; }
    public void setBookValue(BigDecimal bookValue) { this.bookValue = bookValue; }

    public LocalDate getDisposalDate() { return disposalDate; }
    public void setDisposalDate(LocalDate disposalDate) { this.disposalDate = disposalDate; }

    public Long getDisposalJournalEntryId() { return disposalJournalEntryId; }
    public void setDisposalJournalEntryId(Long disposalJournalEntryId) { this.disposalJournalEntryId = disposalJournalEntryId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
