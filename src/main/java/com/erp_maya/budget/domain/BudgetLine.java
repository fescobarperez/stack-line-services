package com.erp_maya.budget.domain;

import com.erp_maya.costcenter.domain.CostCenter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Línea de presupuesto por cuenta y mes (1-12): presupuestado vs real. */
@Entity
@Table(name = "budget_lines")
public class BudgetLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(name = "account_code")
    private String accountCode;

    private String name;

    @Column(name = "is_income", nullable = false)
    private Boolean isIncome = Boolean.FALSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @Column(name = "period_month")
    private Integer periodMonth;

    /** Departamento/agrupación (Ingresos, Costo de Ventas, Gastos Administrativos…). */
    private String department;

    @Column(name = "budgeted_amount")
    private BigDecimal budgetedAmount = BigDecimal.ZERO;

    @Column(name = "actual_amount")
    private BigDecimal actualAmount = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Budget getBudget() { return budget; }
    public void setBudget(Budget budget) { this.budget = budget; }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getIsIncome() { return isIncome; }
    public void setIsIncome(Boolean isIncome) { this.isIncome = isIncome; }

    public CostCenter getCostCenter() { return costCenter; }
    public void setCostCenter(CostCenter costCenter) { this.costCenter = costCenter; }

    public Integer getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(Integer periodMonth) { this.periodMonth = periodMonth; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public BigDecimal getBudgetedAmount() { return budgetedAmount; }
    public void setBudgetedAmount(BigDecimal budgetedAmount) { this.budgetedAmount = budgetedAmount; }

    public BigDecimal getActualAmount() { return actualAmount; }
    public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }
}
