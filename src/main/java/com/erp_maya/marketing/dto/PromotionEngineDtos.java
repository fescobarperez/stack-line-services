package com.erp_maya.marketing.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class PromotionEngineDtos {

    /** Renglón del carrito tal como lo tiene el POS antes de cobrar. */
    @Serdeable
    public record CartItem(@NotNull Long productId, String name, String category,
                           @NotNull BigDecimal quantity, @NotNull BigDecimal unitPrice) {}

    @Serdeable
    public record ApplyRequest(@NotNull List<CartItem> items, String clientType, Long branchId) {}

    /** Descuento ya repartido por renglón: es lo que se persiste en sale_items. */
    @Serdeable
    public record LineDiscount(Long productId, BigDecimal discount, Long promotionId) {}

    @Serdeable
    public record AppliedPromotion(Long promotionId, String name, String label, BigDecimal discount) {}

    @Serdeable
    public record ApplyResponse(List<AppliedPromotion> promotions,
                                List<LineDiscount> lines,
                                BigDecimal totalDiscount) {}
}
