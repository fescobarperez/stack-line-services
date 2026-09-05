package com.erp_maya.accounting.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class JournalEntryDtos {

    private JournalEntryDtos() {}

    @Serdeable
    public record LineRequest(@NotNull Long accountId, BigDecimal debit, BigDecimal credit) {}

    @Serdeable
    public record Request(Long periodId, @NotNull LocalDate entryDate, String entryType,
                          String description, String reference, String sourceType,
                          @NotEmpty @Valid List<LineRequest> lines) {}

    @Serdeable
    public record LineResponse(Long id, Long accountId, String accountCode, String accountName,
                               BigDecimal debit, BigDecimal credit) {}

    @Serdeable
    public record Response(Long id, Long periodId, LocalDate entryDate, String entryType,
                           String description, String reference, String sourceType,
                           BigDecimal totalDebit, BigDecimal totalCredit, String status,
                           List<LineResponse> lines) {}
}
