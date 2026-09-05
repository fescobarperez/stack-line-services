package com.erp_maya.accounting.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

public final class AccountDtos {

    private AccountDtos() {}

    @Serdeable
    public record Request(@NotBlank String code, @NotBlank String name, Long parentId,
                          Integer level, @NotBlank String normalBalance, Boolean allowsEntries) {}

    @Serdeable
    public record Response(Long id, String code, String name, Long parentId, String parentCode,
                           Integer level, String normalBalance, Boolean allowsEntries) {}
}
