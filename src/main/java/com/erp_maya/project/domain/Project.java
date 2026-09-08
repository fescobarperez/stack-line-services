package com.erp_maya.project.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Trabajo con seguimiento de rentabilidad. Puede existir antes de cotizar. */
@Entity
@Table(name = "projects")
public class Project {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "quote_id")
    private Long quoteId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "cost_center_id")
    private Long costCenterId;

    /** Congelado al convertir: renegociar la cotización no lo reescribe. */
    @Column(name = "contracted_amount", nullable = false)
    private BigDecimal contractedAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "GTQ";

    @Column(nullable = false)
    private String status = "draft";

    /** Justificación de haber cerrado con menos facturado que ejecutado. */
    @Column(name = "close_note")
    private String closeNote;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private String notes;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getCostCenterId() { return costCenterId; }
    public void setCostCenterId(Long costCenterId) { this.costCenterId = costCenterId; }
    public BigDecimal getContractedAmount() { return contractedAmount; }
    public void setContractedAmount(BigDecimal v) { this.contractedAmount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCloseNote() { return closeNote; }
    public void setCloseNote(String closeNote) { this.closeNote = closeNote; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
}
