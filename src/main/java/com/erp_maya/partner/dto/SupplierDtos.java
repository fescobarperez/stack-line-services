package com.erp_maya.partner.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public final class SupplierDtos {

    private SupplierDtos() {}

    @Serdeable
    public record Request(@NotBlank String name, String nit, String contact, String phone,
                          String paymentTerms, BigDecimal balance, String status) {}

    @Serdeable
    public record Response(Long id, String name, String nit, String contact, String phone,
                           String paymentTerms, BigDecimal balance, String status) {}
}
