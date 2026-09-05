package com.erp_maya.security.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class RoleDtos {

    private RoleDtos() {}

    @Serdeable
    public record Request(@NotBlank String name, String description, List<String> permissions) {}

    @Serdeable
    public record Response(Long id, String name, String description, List<String> permissions) {}
}
