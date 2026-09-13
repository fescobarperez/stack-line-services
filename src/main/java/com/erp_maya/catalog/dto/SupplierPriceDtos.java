package com.erp_maya.catalog.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Comparación de precios entre los proveedores de un producto, y su ranking. */
public class SupplierPriceDtos {

    /**
     * Un proveedor dentro de la comparación de un producto.
     *
     * `diffVsBestPct` es cuánto más caro está que el más barato; el más barato
     * lleva cero. `previousCost` y `changePct` salen del historial: sin fila
     * anterior vienen nulos, que es el caso de todo precio recién sembrado.
     */
    @Serdeable
    public record SupplierPriceRow(Long supplierId, String supplierName,
                                   BigDecimal unitCost, Boolean preferred,
                                   BigDecimal diffVsBest, BigDecimal diffVsBestPct,
                                   Instant validFrom,
                                   BigDecimal previousCost, BigDecimal changePct) {}

    /** `spreadPct`: cuánto separa al más caro del más barato. */
    @Serdeable
    public record ProductComparison(Long productId, String sku, String productName, String unit,
                                    BigDecimal bestCost, BigDecimal worstCost, BigDecimal spreadPct,
                                    Boolean preferredIsCheapest,
                                    List<SupplierPriceRow> suppliers) {}

    /**
     * Un proveedor en el ranking global.
     *
     * `productsQuoted` es en cuántos productos aparece; `productsCompeting`, en
     * cuántos de esos hay al menos otro proveedor. La distinción importa: en un
     * producto que solo él surte gana por default, y contar esas victorias
     * inflaría el ranking de quien surte cosas exclusivas.
     *
     * `avgDeviationPct` es el promedio de cuánto por encima del mejor precio
     * está, medido SOLO sobre los productos donde compite. Cero = siempre es el
     * más barato de los que compiten.
     */
    @Serdeable
    public record SupplierRankRow(Long supplierId, String supplierName,
                                  Integer productsQuoted, Integer productsCompeting,
                                  Integer productsWon, BigDecimal avgDeviationPct) {}
}
