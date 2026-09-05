package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CategoryResponse(
        Long id,
        String name,
        String icon
) {
}
