package com.erp_maya.accounting.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public final class AccountingPeriodDtos {

    private AccountingPeriodDtos() {}

    @Serdeable
    public record Request(@NotBlank String name, @NotNull LocalDate startDate,
                          @NotNull LocalDate endDate, String status) {}

    @Serdeable
    public record Response(Long id, String name, LocalDate startDate, LocalDate endDate, String status) {}
}
