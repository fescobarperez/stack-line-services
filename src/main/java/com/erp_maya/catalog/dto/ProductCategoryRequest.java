package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProductCategoryRequest(Long categoryId) {
}
