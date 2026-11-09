package com.erp_maya.security.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

/** Token de servicio que el ERP emite a agents-services, uno por empresa. */
public final class ServiceTokenDtos {

    private ServiceTokenDtos() {}

    @Serdeable
    public record Request(@NotNull Long companyId) {}

    @Serdeable
    public record Response(String token, int expiresIn) {}
}
