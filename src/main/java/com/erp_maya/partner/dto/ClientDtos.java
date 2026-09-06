package com.erp_maya.partner.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public final class ClientDtos {

    private ClientDtos() {}

    @Serdeable
    public record Request(@NotBlank String name, String nit, String clientType, String address,
                          String phone, String email, BigDecimal creditLimit, Integer paymentTerms,
                          BigDecimal openingBalance, String status) {}

    @Serdeable
    /** `balance` es derivado (v_client_balance), no un campo del cliente. */
    public record Response(Long id, String name, String nit, String clientType, String address,
                           String phone, String email, BigDecimal creditLimit, Integer paymentTerms,
                           BigDecimal balance, BigDecimal openingBalance, String status) {}
}
