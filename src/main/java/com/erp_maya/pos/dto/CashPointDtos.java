package com.erp_maya.pos.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CashPointDtos {

    @Serdeable
    public record Request(@NotNull Long branchId, @NotBlank String code,
                          @NotBlank String name, String status) {}

    /**
     * `openSessionId` / `openUserId` describen quién la está ocupando ahora:
     * es lo que el frontend necesita para no ofrecer una caja ya tomada.
     */
    @Serdeable
    public record Response(Long id, Long branchId, String branchName,
                           String code, String name, String status,
                           Long openSessionId, Long openUserId, String openUserName) {}
}
