package com.erp_maya.catalog.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.domain.ProductSupplier;
import com.erp_maya.catalog.domain.ProductSupplierPrice;
import com.erp_maya.catalog.dto.SupplierPriceDtos;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.catalog.repository.ProductSupplierPriceRepository;
import com.erp_maya.catalog.repository.ProductSupplierRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.Supplier;
import com.erp_maya.partner.repository.SupplierRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comparación de precios entre proveedores.
 *
 * Dos vistas de la misma relación: por producto —quién lo da más barato— y el
 * ranking global —quién da mejor precio en general—.
 *
 * El ranking se calcula en Java y no en SQL a propósito. La consulta agregada
 * es escribible, pero las @Query nativas de este proyecto ya dieron problemas
 * mapeando varias columnas, y el catálogo cabe holgado en memoria: son unas
 * pocas miles de filas de product_suppliers por empresa. Si algún día no
 * cupiera, esto se cambia por una vista materializada sin tocar el contrato.
 */
@Singleton
public class SupplierPriceService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ProductRepository products;
    private final ProductSupplierRepository productSuppliers;
    private final ProductSupplierPriceRepository priceHistory;
    private final SupplierRepository suppliers;
    private final TenantContext tenant;

    public SupplierPriceService(ProductRepository products, ProductSupplierRepository productSuppliers,
                                ProductSupplierPriceRepository priceHistory, SupplierRepository suppliers,
                                TenantContext tenant) {
        this.products = products;
        this.productSuppliers = productSuppliers;
        this.priceHistory = priceHistory;
        this.suppliers = suppliers;
        this.tenant = tenant;
    }

    /** Los proveedores de un producto, del más barato al más caro. */
    @Transactional
    public SupplierPriceDtos.ProductComparison compareProduct(Long productId) {
        Long companyId = tenant.getCompanyId();
        Product product = products.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + productId + " no encontrado"));

        List<ProductSupplier> relaciones = productSuppliers
                .findByCompanyIdAndProductIdOrderByPreferredDesc(companyId, productId).stream()
                .sorted(Comparator.comparing(ProductSupplier::getUnitCost))
                .toList();
        if (relaciones.isEmpty()) {
            return new SupplierPriceDtos.ProductComparison(product.getId(), product.getSku(), product.getName(),
                    product.getUnit(), null, null, null, null, List.of());
        }

        Map<Long, String> nombres = nombresDeProveedor(companyId);
        BigDecimal mejor = relaciones.get(0).getUnitCost();
        BigDecimal peor = relaciones.get(relaciones.size() - 1).getUnitCost();

        List<SupplierPriceDtos.SupplierPriceRow> filas = new ArrayList<>();
        for (ProductSupplier relacion : relaciones) {
            BigDecimal costo = relacion.getUnitCost();
            List<ProductSupplierPrice> historial = priceHistory
                    .findByCompanyIdAndProductSupplierIdOrderByValidFromDesc(companyId, relacion.getId());
            // El [0] es el vigente; el [1], el que regía antes.
            ProductSupplierPrice vigente = historial.isEmpty() ? null : historial.get(0);
            ProductSupplierPrice anterior = historial.size() > 1 ? historial.get(1) : null;

            filas.add(new SupplierPriceDtos.SupplierPriceRow(
                    relacion.getSupplierId(),
                    nombres.getOrDefault(relacion.getSupplierId(), "Proveedor " + relacion.getSupplierId()),
                    dinero(costo),
                    relacion.getPreferred(),
                    dinero(costo.subtract(mejor)),
                    porcentaje(costo.subtract(mejor), mejor),
                    vigente == null ? null : vigente.getValidFrom(),
                    anterior == null ? null : dinero(anterior.getUnitCost()),
                    anterior == null ? null : porcentaje(costo.subtract(anterior.getUnitCost()), anterior.getUnitCost())));
        }

        boolean preferidoEsElMasBarato = relaciones.get(0).getPreferred() != null
                && relaciones.get(0).getPreferred();
        return new SupplierPriceDtos.ProductComparison(
                product.getId(), product.getSku(), product.getName(), product.getUnit(),
                dinero(mejor), dinero(peor), porcentaje(peor.subtract(mejor), mejor),
                preferidoEsElMasBarato, filas);
    }

    /**
     * Ranking de proveedores por qué tan buen precio dan.
     *
     * Ordena por desviación promedio ascendente —el que menos se aleja del mejor
     * precio va primero— y desempata por victorias. No ordena por victorias
     * primero a propósito: quien gana un producto y queda 40% arriba en otros
     * cinco es peor socio que quien nunca gana pero siempre está 2% arriba.
     *
     * Un proveedor sin ningún producto en competencia no tiene desviación que
     * medir; va al final, porque no hay evidencia de que dé buen precio.
     *
     * Con `productId` se acota a los proveedores de ese producto, pero las
     * métricas se siguen calculando sobre TODO el catálogo: la gracia es ver
     * cómo se comportan en general los que surten este producto, no repetir la
     * lista de precios que ya da compareProduct. Un producto sin proveedores
     * devuelve vacío.
     */
    @Transactional
    public List<SupplierPriceDtos.SupplierRankRow> ranking(Integer limit, Long productId) {
        Long companyId = tenant.getCompanyId();
        List<ProductSupplier> todas = productSuppliers.findByCompanyId(companyId);
        if (todas.isEmpty()) return List.of();

        Set<Long> soloEstos = null;
        if (productId != null) {
            soloEstos = todas.stream()
                    .filter(relacion -> productId.equals(relacion.getProductId()))
                    .map(ProductSupplier::getSupplierId)
                    .collect(Collectors.toSet());
            if (soloEstos.isEmpty()) return List.of();
        }
        final Set<Long> filtro = soloEstos;

        Map<Long, List<ProductSupplier>> porProducto = todas.stream()
                .collect(Collectors.groupingBy(ProductSupplier::getProductId));

        Map<Long, int[]> conteos = new HashMap<>();       // [ofertados, compitiendo, ganados]
        Map<Long, List<BigDecimal>> desviaciones = new HashMap<>();

        for (List<ProductSupplier> delProducto : porProducto.values()) {
            BigDecimal mejor = delProducto.stream().map(ProductSupplier::getUnitCost)
                    .min(Comparator.naturalOrder()).orElse(null);
            if (mejor == null || mejor.signum() <= 0) continue;
            boolean hayCompetencia = delProducto.size() > 1;

            for (ProductSupplier relacion : delProducto) {
                int[] c = conteos.computeIfAbsent(relacion.getSupplierId(), k -> new int[3]);
                c[0]++;
                if (!hayCompetencia) continue;
                c[1]++;
                if (relacion.getUnitCost().compareTo(mejor) == 0) c[2]++;
                desviaciones.computeIfAbsent(relacion.getSupplierId(), k -> new ArrayList<>())
                        .add(porcentaje(relacion.getUnitCost().subtract(mejor), mejor));
            }
        }

        Map<Long, String> nombres = nombresDeProveedor(companyId);
        List<SupplierPriceDtos.SupplierRankRow> filas = conteos.entrySet().stream()
                .filter(entrada -> filtro == null || filtro.contains(entrada.getKey()))
                .map(entrada -> {
                    Long supplierId = entrada.getKey();
                    int[] c = entrada.getValue();
                    List<BigDecimal> desvios = desviaciones.getOrDefault(supplierId, List.of());
                    BigDecimal promedio = desvios.isEmpty() ? null
                            : desvios.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .divide(BigDecimal.valueOf(desvios.size()), 2, RoundingMode.HALF_UP);
                    return new SupplierPriceDtos.SupplierRankRow(
                            supplierId, nombres.getOrDefault(supplierId, "Proveedor " + supplierId),
                            c[0], c[1], c[2], promedio);
                })
                .sorted(Comparator
                        .comparing((SupplierPriceDtos.SupplierRankRow f) -> f.avgDeviationPct() == null)
                        .thenComparing(f -> f.avgDeviationPct() == null ? BigDecimal.ZERO : f.avgDeviationPct())
                        .thenComparing(f -> -f.productsWon())
                        .thenComparing(SupplierPriceDtos.SupplierRankRow::supplierName))
                .toList();

        return limit != null && limit > 0 && limit < filas.size() ? filas.subList(0, limit) : filas;
    }

    private Map<Long, String> nombresDeProveedor(Long companyId) {
        return suppliers.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));
    }

    private BigDecimal dinero(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** Diferencia como porcentaje de la base. Base cero = sin porcentaje que dar. */
    private BigDecimal porcentaje(BigDecimal diferencia, BigDecimal base) {
        if (base == null || base.signum() == 0) return null;
        return diferencia.multiply(CIEN).divide(base, 2, RoundingMode.HALF_UP);
    }
}
