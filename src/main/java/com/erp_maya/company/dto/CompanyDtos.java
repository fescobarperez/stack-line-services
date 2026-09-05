package com.erp_maya.company.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** DTOs de la empresa (tenant). */
public final class CompanyDtos {

    private CompanyDtos() {}

    @Serdeable
    public record Request(@NotBlank String name, String nit, String plan, String status) {}

    @Serdeable
    public record Response(Long id, String name, String nit, String plan, String status) {}
}
