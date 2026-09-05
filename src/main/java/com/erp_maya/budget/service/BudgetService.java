package com.erp_maya.budget.service;

import com.erp_maya.budget.domain.Budget;
import com.erp_maya.budget.domain.BudgetLine;
import com.erp_maya.budget.dto.BudgetDtos;
import com.erp_maya.budget.repository.BudgetRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.costcenter.repository.CostCenterRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Singleton
public class BudgetService {

    private final BudgetRepository budgets;
    private final CostCenterRepository costCenters;
    private final TenantContext tenant;

    public BudgetService(BudgetRepository budgets, CostCenterRepository costCenters, TenantContext tenant) {
        this.budgets = budgets;
        this.costCenters = costCenters;
        this.tenant = tenant;
    }

    @Transactional
    public List<BudgetDtos.Response> list() {
        return budgets.findByCompanyIdOrderByYearDesc(tenant.getCompanyId())
                .stream().map(BudgetService::toResponse).toList();
    }

    @Transactional
    public BudgetDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public BudgetDtos.Response create(BudgetDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        Budget budget = new Budget();
        budget.setCompanyId(companyId);
        budget.setYear(req.year());
        budget.setName(req.name());
        budget.setStatus(req.status() != null ? req.status() : "draft");

        if (req.lines() != null) {
            for (BudgetDtos.LineRequest lr : req.lines()) {
                addExpanding(budget, lr);
            }
        }
        recomputeTotals(budget);
        return toResponse(budgets.save(budget));
    }

    @Transactional
    public BudgetDtos.LineResponse addLine(Long budgetId, BudgetDtos.LineRequest req) {
        Budget budget = find(budgetId);
        BudgetLine first = addExpanding(budget, req);
        recomputeTotals(budget);
        budgets.update(budget);
        return toLineResponse(first);
    }

    /** Agrega la línea; si periodMonth es null, la expande a los 12 meses. */
    private BudgetLine addExpanding(Budget budget, BudgetDtos.LineRequest lr) {
        BudgetLine first = null;
        if (lr.periodMonth() == null) {
            for (int m = 1; m <= 12; m++) {
                BudgetLine line = toLine(lr);
                line.setPeriodMonth(m);
                budget.addLine(line);
                if (first == null) first = line;
            }
        } else {
            first = toLine(lr);
            budget.addLine(first);
        }
        return first;
    }

    private BudgetLine toLine(BudgetDtos.LineRequest lr) {
        BudgetLine line = new BudgetLine();
        line.setAccountCode(lr.accountCode());
        line.setName(lr.name());
        line.setDepartment(lr.department());
        line.setIsIncome(lr.isIncome() != null ? lr.isIncome() : Boolean.FALSE);
        line.setPeriodMonth(lr.periodMonth());
        line.setBudgetedAmount(lr.budgetedAmount() != null ? lr.budgetedAmount() : BigDecimal.ZERO);
        line.setActualAmount(lr.actualAmount() != null ? lr.actualAmount() : BigDecimal.ZERO);
        if (lr.costCenterId() != null) {
            costCenters.findByIdAndCompanyId(lr.costCenterId(), tenant.getCompanyId()).ifPresent(line::setCostCenter);
        }
        return line;
    }

    /** Recalcula el presupuesto total y el % de ejecución (real / presupuestado). */
    private void recomputeTotals(Budget budget) {
        BigDecimal budgeted = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        for (BudgetLine l : budget.getLines()) {
            budgeted = budgeted.add(l.getBudgetedAmount() != null ? l.getBudgetedAmount() : BigDecimal.ZERO);
            actual = actual.add(l.getActualAmount() != null ? l.getActualAmount() : BigDecimal.ZERO);
        }
        budget.setTotalBudget(budgeted);
        budget.setExecutionPct(budgeted.signum() == 0 ? BigDecimal.ZERO
                : actual.multiply(new BigDecimal("100")).divide(budgeted, 2, java.math.RoundingMode.HALF_UP));
    }

    private Budget find(Long id) {
        return budgets.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Presupuesto " + id + " no encontrado"));
    }

    private static BudgetDtos.LineResponse toLineResponse(BudgetLine l) {
        return new BudgetDtos.LineResponse(l.getId(), l.getAccountCode(), l.getName(), l.getDepartment(), l.getIsIncome(),
                l.getCostCenter() != null ? l.getCostCenter().getId() : null,
                l.getPeriodMonth(), l.getBudgetedAmount(), l.getActualAmount());
    }

    private static BudgetDtos.Response toResponse(Budget b) {
        var lines = b.getLines().stream().map(BudgetService::toLineResponse).toList();
        return new BudgetDtos.Response(b.getId(), b.getYear(), b.getName(), b.getStatus(),
                b.getTotalBudget(), b.getExecutionPct(), lines);
    }
}
