package com.erp_maya.company.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

/** DTOs del establecimiento SAT. */
public final class EstablishmentDtos {

    private EstablishmentDtos() {}

    @Serdeable
    public record Request(@NotNull Integer satCode, String commercialName, String address,
                          String phone, String status) {}

    @Serdeable
    public record Response(Long id, Integer satCode, String commercialName, String address,
                           String phone, String status) {}
}
