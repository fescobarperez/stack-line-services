package com.erp_maya.partner.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class SupplierWebsiteDtos {

    private SupplierWebsiteDtos() {}

    @Serdeable
    public record Request(@NotNull Long supplierId,
                          @NotBlank String name,
                          @NotBlank String baseUrl,
                          String websiteType,
                          String status,
                          Boolean crawlEnabled) {}

    @Serdeable
    public record Response(Long id, Long supplierId, String name, String baseUrl,
                           String websiteType, String status, Boolean crawlEnabled,
                           Instant createdAt, Instant updatedAt) {}
}
