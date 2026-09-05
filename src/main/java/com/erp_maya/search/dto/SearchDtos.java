package com.erp_maya.search.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;

/** Resultados de la búsqueda global (⌘K): productos y clientes. */
public final class SearchDtos {

    private SearchDtos() {}

    @Serdeable
    public record ProductHit(Long id, String sku, String name, BigDecimal price) {}

    @Serdeable
    public record ClientHit(Long id, String name, String nit) {}

    @Serdeable
    public record SearchResponse(List<ProductHit> products, List<ClientHit> clients) {}
}
