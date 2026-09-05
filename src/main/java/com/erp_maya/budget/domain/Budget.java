package com.erp_maya.budget.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Presupuesto anual. Sus líneas van en {@link BudgetLine}. */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Integer year;

    private String name;

    private String status = "draft";

    @Column(name = "total_budget")
    private BigDecimal totalBudget = BigDecimal.ZERO;

    @Column(name = "execution_pct")
    private BigDecimal executionPct = BigDecimal.ZERO;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BudgetLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addLine(BudgetLine line) {
        line.setBudget(this);
        line.setCompanyId(this.companyId);
        this.lines.add(line);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalBudget() { return totalBudget; }
    public void setTotalBudget(BigDecimal totalBudget) { this.totalBudget = totalBudget; }

    public BigDecimal getExecutionPct() { return executionPct; }
    public void setExecutionPct(BigDecimal executionPct) { this.executionPct = executionPct; }

    public List<BudgetLine> getLines() { return lines; }
    public void setLines(List<BudgetLine> lines) { this.lines = lines; }
}
