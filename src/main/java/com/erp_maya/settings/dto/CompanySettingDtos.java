package com.erp_maya.settings.dto;

import io.micronaut.serde.annotation.Serdeable;

public final class CompanySettingDtos {

    private CompanySettingDtos() {}

    @Serdeable
    public record Request(String settingValue, String category) {}

    @Serdeable
    public record Response(Long id, String settingKey, String settingValue, String category) {}
}
