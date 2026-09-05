package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record CategoryRequest(
        @NotBlank String name,
        String icon
) {
}
