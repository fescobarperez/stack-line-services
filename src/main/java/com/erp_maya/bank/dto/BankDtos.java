package com.erp_maya.bank.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class BankDtos {

    private BankDtos() {}

    @Serdeable
    public record AccountRequest(@NotBlank String accountCode, String bankName, String accountType,
                                 String currency, String accountNumber, String alias, BigDecimal balance,
                                 String status, LocalDate openedDate, Long glAccountId) {}

    @Serdeable
    public record AccountResponse(Long id, String accountCode, String bankName, String accountType,
                                  String currency, String accountNumber, String alias, BigDecimal balance,
                                  BigDecimal bookBalance, String status, LocalDate openedDate,
                                  LocalDate lastMovementDate, Long glAccountId) {}

    /** Movimiento: amount con signo (positivo abona, negativo carga). */
    @Serdeable
    public record MovementRequest(@NotNull BigDecimal amount, String movementType, String description,
                                  String reference, LocalDate movementDate) {}

    @Serdeable
    public record MovementResponse(Long id, Long bankAccountId, LocalDate movementDate, String description,
                                   String reference, String movementType, BigDecimal amount,
                                   BigDecimal runningBalance, Boolean reconciled) {}

    @Serdeable
    public record ReconcileRequest(@NotNull LocalDate statementDate, @NotNull BigDecimal bankBalance, String notes) {}

    @Serdeable
    public record ReconcileResponse(Long id, Long bankAccountId, LocalDate statementDate,
                                    BigDecimal bookBalance, BigDecimal bankBalance, BigDecimal difference,
                                    String status, String notes) {}
}
