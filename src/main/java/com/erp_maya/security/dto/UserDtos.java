package com.erp_maya.security.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {}

    @Serdeable
    /**
     * `branchId` es dónde trabaja; `branchIds` qué sucursales cubre como
     * aprobador. `managerId` y `authLevelId` son el árbol de autorizaciones.
     */
    public record Request(@NotBlank String name, String email, String password,
                          Long roleId, Long branchId, String status,
                          Long managerId, Long authLevelId, java.util.List<Long> branchIds) {}

    @Serdeable
    public record Response(Long id, String name, String email, Long roleId, String roleName,
                           Long branchId, String branchName, String status, Instant lastSeenAt,
                           Long managerId, String managerName,
                           Long authLevelId, String authLevelName, Short authLevelRank,
                           java.util.List<Long> branchIds) {}
}
