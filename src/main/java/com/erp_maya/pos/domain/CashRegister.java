package com.erp_maya.pos.domain;

import com.erp_maya.company.domain.Branch;
import com.erp_maya.security.domain.User;
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

/**
 * Turno/arqueo de caja: la sesión apertura → ventas → cierre de UN cajero sobre
 * UNA caja ({@link CashPoint}). La caja es el recurso duradero; esto es el uso.
 * La base garantiza con índices únicos parciales que solo haya un turno abierto
 * por caja y uno por cajero.
 */
@Entity
@Table(name = "cash_registers")
public class CashRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** La caja física ocupada por este turno. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_point_id", nullable = false)
    private CashPoint cashPoint;

    /**
     * Fecha operativa del turno. No se deriva de openedAt al consultar: se fija
     * al abrir, para que un turno que cruce medianoche siga perteneciendo al día
     * en que empezó.
     */
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opening_amount")
    private BigDecimal openingAmount = BigDecimal.ZERO;

    @Column(name = "closing_amount")
    private BigDecimal closingAmount;

    @Column(name = "sales_total")
    private BigDecimal salesTotal = BigDecimal.ZERO;

    @Column(name = "sales_cash")
    private BigDecimal salesCash = BigDecimal.ZERO;

    @Column(name = "sales_card")
    private BigDecimal salesCard = BigDecimal.ZERO;

    private BigDecimal refunds = BigDecimal.ZERO;

    private BigDecimal difference;

    private String status = "open";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public CashPoint getCashPoint() { return cashPoint; }
    public void setCashPoint(CashPoint cashPoint) { this.cashPoint = cashPoint; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public BigDecimal getOpeningAmount() { return openingAmount; }
    public void setOpeningAmount(BigDecimal openingAmount) { this.openingAmount = openingAmount; }

    public BigDecimal getClosingAmount() { return closingAmount; }
    public void setClosingAmount(BigDecimal closingAmount) { this.closingAmount = closingAmount; }

    public BigDecimal getSalesTotal() { return salesTotal; }
    public void setSalesTotal(BigDecimal salesTotal) { this.salesTotal = salesTotal; }

    public BigDecimal getSalesCash() { return salesCash; }
    public void setSalesCash(BigDecimal salesCash) { this.salesCash = salesCash; }

    public BigDecimal getSalesCard() { return salesCard; }
    public void setSalesCard(BigDecimal salesCard) { this.salesCard = salesCard; }

    public BigDecimal getRefunds() { return refunds; }
    public void setRefunds(BigDecimal refunds) { this.refunds = refunds; }

    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
