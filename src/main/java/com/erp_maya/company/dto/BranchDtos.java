package com.erp_maya.company.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** DTOs de la sucursal. */
public final class BranchDtos {

    private BranchDtos() {}

    @Serdeable
    public record Request(@NotBlank String name, Long establishmentId, String address, String status) {}

    @Serdeable
    public record Response(Long id, String name, Long establishmentId, String establishmentName,
                           String address, String status) {}
}
