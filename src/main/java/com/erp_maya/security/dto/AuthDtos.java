package com.erp_maya.security.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class AuthDtos {

    private AuthDtos() {}

    @Serdeable
    public record LoginRequest(@NotBlank String companyCode,
                               @NotBlank String email,
                               @NotBlank String password) {}

    @Serdeable
    public record LoginResponse(String token, UserInfo user) {}

    @Serdeable
    /** `authLevel*` es nulo para quien no aprueba nada: la UI oculta lo que no aplica. */
    public record UserInfo(Long id, String name, String email,
                           Long companyId, String companyName,
                           Long roleId, String roleName,
                           Long authLevelId, String authLevelName, Short authLevelRank,
                           List<String> permissions) {}
}
