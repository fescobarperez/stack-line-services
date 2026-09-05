package com.erp_maya.security.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {}

    @Serdeable
    public record Request(@NotBlank String name, String email, String password,
                          Long roleId, Long branchId, String status) {}

    @Serdeable
    public record Response(Long id, String name, String email, Long roleId, String roleName,
                           Long branchId, String branchName, String status, Instant lastSeenAt) {}
}
