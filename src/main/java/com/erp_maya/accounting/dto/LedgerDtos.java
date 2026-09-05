package com.erp_maya.accounting.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Respuestas del mayor general: balance de comprobación (con apertura) y mayor por cuenta. */
public final class LedgerDtos {

    private LedgerDtos() {}

    /** Fila del balance de comprobación con columnas de apertura, movimientos y saldo final. */
    @Serdeable
    public record TrialRow(Long accountId, String code, String name,
                           BigDecimal openingDebit, BigDecimal openingCredit,
                           BigDecimal periodDebit, BigDecimal periodCredit,
                           BigDecimal closingDebit, BigDecimal closingCredit) {}

    @Serdeable
    public record TrialBalance(Long periodId, List<TrialRow> rows, TrialRow totals, boolean balanced) {}

    /** Movimiento del mayor con saldo corrido (running balance). */
    @Serdeable
    public record Movement(LocalDate date, String reference, String description,
                           BigDecimal debit, BigDecimal credit, BigDecimal balance) {}

    @Serdeable
    public record AccountLedger(Long accountId, String code, String name,
                                BigDecimal openingBalance, List<Movement> movements,
                                BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal closingBalance) {}
}
