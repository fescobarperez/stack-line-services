package com.erp_maya.payroll.domain;

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

/** Renglón de nómina por empleado: salario base, bonos y deducciones → líquido. */
@Entity
@Table(name = "payroll_entries")
public class PayrollEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_period_id", nullable = false)
    private PayrollPeriod period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "base_salary")
    private BigDecimal baseSalary = BigDecimal.ZERO;

    private BigDecimal bonuses = BigDecimal.ZERO;

    @Column(name = "igss_deduction")
    private BigDecimal igssDeduction = BigDecimal.ZERO;

    @Column(name = "isr_deduction")
    private BigDecimal isrDeduction = BigDecimal.ZERO;

    @Column(name = "other_deductions")
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "net_pay")
    private BigDecimal netPay = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public PayrollPeriod getPeriod() { return period; }
    public void setPeriod(PayrollPeriod period) { this.period = period; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getBonuses() { return bonuses; }
    public void setBonuses(BigDecimal bonuses) { this.bonuses = bonuses; }

    public BigDecimal getIgssDeduction() { return igssDeduction; }
    public void setIgssDeduction(BigDecimal igssDeduction) { this.igssDeduction = igssDeduction; }

    public BigDecimal getIsrDeduction() { return isrDeduction; }
    public void setIsrDeduction(BigDecimal isrDeduction) { this.isrDeduction = isrDeduction; }

    public BigDecimal getOtherDeductions() { return otherDeductions; }
    public void setOtherDeductions(BigDecimal otherDeductions) { this.otherDeductions = otherDeductions; }

    public BigDecimal getNetPay() { return netPay; }
    public void setNetPay(BigDecimal netPay) { this.netPay = netPay; }
}
