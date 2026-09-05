package com.erp_maya.loyalty.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class LoyaltyDtos {

    private LoyaltyDtos() {}

    @Serdeable
    public record AccountRequest(@NotBlank String memberCode, Long clientId, String name, String nit,
                                 String phone, String email, String tier, LocalDate joinDate) {}

    @Serdeable
    public record AccountResponse(Long id, String memberCode, Long clientId, String name, String nit,
                                  String phone, String email, Integer pointsBalance, BigDecimal totalSpent,
                                  String tier, LocalDate joinDate, LocalDate lastPurchaseDate,
                                  Integer pointsEarned, Integer pointsRedeemed) {}

    /** Movimiento: type earned|redeemed|bonus; points con signo. */
    @Serdeable
    public record MovementRequest(@NotBlank String movementType, @NotNull Integer points, Long saleId,
                                  String reference, BigDecimal amount) {}

    @Serdeable
    public record MovementResponse(Long id, Long accountId, String movementType, Integer points,
                                   Long saleId, String reference, BigDecimal amount, LocalDate movementDate) {}
}
