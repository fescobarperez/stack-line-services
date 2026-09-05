package com.erp_maya.budget.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class BudgetDtos {

    private BudgetDtos() {}

    /** periodMonth null → se expande a los 12 meses con el mismo monto. */
    @Serdeable
    public record LineRequest(String accountCode, String name, String department, Boolean isIncome, Long costCenterId,
                              Integer periodMonth, BigDecimal budgetedAmount, BigDecimal actualAmount) {}

    @Serdeable
    public record Request(@NotNull Integer year, String name, String status, @Valid List<LineRequest> lines) {}

    @Serdeable
    public record LineResponse(Long id, String accountCode, String name, String department, Boolean isIncome,
                               Long costCenterId, Integer periodMonth, BigDecimal budgetedAmount, BigDecimal actualAmount) {}

    @Serdeable
    public record Response(Long id, Integer year, String name, String status, BigDecimal totalBudget,
                           BigDecimal executionPct, List<LineResponse> lines) {}
}
