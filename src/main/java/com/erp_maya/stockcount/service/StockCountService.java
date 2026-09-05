package com.erp_maya.stockcount.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.inventory.domain.ProductStock;
import com.erp_maya.inventory.repository.ProductStockRepository;
import com.erp_maya.inventory.service.StockService;
import com.erp_maya.stockcount.domain.StockCount;
import com.erp_maya.stockcount.domain.StockCountItem;
import com.erp_maya.stockcount.dto.StockCountDtos;
import com.erp_maya.stockcount.repository.StockCountRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Toma física. Crea la sesión tomando foto del stock; al cerrar aplica el ajuste de kardex. */
@Singleton
public class StockCountService {

    private final StockCountRepository counts;
    private final ProductRepository products;
    private final ProductStockRepository stock;
    private final BranchRepository branches;
    private final StockService stockService;
    private final TenantContext tenant;

    public StockCountService(StockCountRepository counts, ProductRepository products, ProductStockRepository stock,
                             BranchRepository branches, StockService stockService, TenantContext tenant) {
        this.counts = counts;
        this.products = products;
        this.stock = stock;
        this.branches = branches;
        this.stockService = stockService;
        this.tenant = tenant;
    }

    @Transactional
    public Page<StockCountDtos.Response> list(Pageable pageable) {
        return counts.findByCompanyIdOrderByCountDateDesc(tenant.getCompanyId(), pageable)
                .map(StockCountService::toResponse);
    }

    @Transactional
    public StockCountDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public StockCountDtos.Response create(StockCountDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        Branch branch = branches.findByIdAndCompanyId(req.branchId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal " + req.branchId() + " no encontrada"));

        StockCount count = new StockCount();
        count.setCompanyId(companyId);
        count.setBranch(branch);
        count.setDocNumber(req.docNumber() != null && !req.docNumber().isBlank()
                ? req.docNumber() : "CNT-" + Instant.now().toEpochMilli());
        count.setCountDate(req.countDate() != null ? req.countDate() : LocalDate.now());
        count.setResponsible(req.responsible());
        count.setCategory(req.category());
        count.setCategoryLabel(req.categoryLabel());
        count.setNotes(req.notes());
        count.setStatus("in_progress");

        if (req.items() != null && !req.items().isEmpty()) {
            for (StockCountDtos.ItemRequest ir : req.items()) {
                Product product = products.findByIdAndCompanyId(ir.productId(), companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Producto " + ir.productId() + " no encontrado"));
                addItem(count, product, ir.systemQty(), ir.countedQty(), ir.lineNotes());
            }
        } else {
            // Foto del stock actual de la sucursal: un renglón por producto (suma de lotes).
            Map<Long, BigDecimal> byProduct = new LinkedHashMap<>();
            Map<Long, Product> productRef = new LinkedHashMap<>();
            for (ProductStock ps : stock.findByCompanyIdAndBranchId(companyId, req.branchId())) {
                if (ps.getProduct() == null) continue;
                Long pid = ps.getProduct().getId();
                byProduct.merge(pid, ps.getQuantity() != null ? ps.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
                productRef.putIfAbsent(pid, ps.getProduct());
            }
            String cat = req.category();
            boolean filterCat = cat != null && !cat.isBlank() && !"all".equalsIgnoreCase(cat);
            for (Map.Entry<Long, BigDecimal> e : byProduct.entrySet()) {
                Product product = productRef.get(e.getKey());
                if (filterCat) {
                    String pcat = product.getCategory() != null ? product.getCategory().getName() : null;
                    if (pcat == null || !pcat.equalsIgnoreCase(req.categoryLabel()) && !pcat.equalsIgnoreCase(cat)) continue;
                }
                addItem(count, product, e.getValue(), null, null);
            }
        }
        return toResponse(counts.save(count));
    }

    /** Guarda cantidades contadas + notas por renglón; opcionalmente cambia el estado. */
    @Transactional
    public StockCountDtos.Response saveCounts(Long id, StockCountDtos.CountUpdate req) {
        StockCount count = find(id);
        if (req.lines() != null) {
            Map<Long, StockCountItem> byId = new LinkedHashMap<>();
            for (StockCountItem it : count.getItems()) byId.put(it.getId(), it);
            for (StockCountDtos.CountLine cl : req.lines()) {
                StockCountItem item = byId.get(cl.itemId());
                if (item == null) continue;
                item.setCountedQty(cl.countedQty());
                item.setLineNotes(cl.lineNotes());
                item.setDifference(cl.countedQty() != null ? cl.countedQty().subtract(item.getSystemQty()) : null);
            }
        }
        if (req.status() != null && !req.status().isBlank()) {
            count.setStatus(req.status());
        }
        return toResponse(counts.update(count));
    }

    /** Cierra el conteo (status=completed) y aplica el ajuste de inventario por cada diferencia. */
    @Transactional
    public StockCountDtos.Response close(Long id) {
        StockCount count = find(id);
        if ("completed".equals(count.getStatus())) {
            throw new IllegalStateException("El conteo ya está cerrado.");
        }
        for (StockCountItem item : count.getItems()) {
            BigDecimal diff = item.getDifference();
            if (diff != null && diff.compareTo(BigDecimal.ZERO) != 0) {
                stockService.applyMovement(item.getProduct(), count.getBranch(), null, "adjustment",
                        diff, "stock_count", count.getDocNumber(), "");
            }
        }
        count.setStatus("completed");
        return toResponse(counts.update(count));
    }

    private static void addItem(StockCount count, Product product, BigDecimal systemQty, BigDecimal countedQty, String lineNotes) {
        StockCountItem item = new StockCountItem();
        item.setProduct(product);
        item.setSystemQty(systemQty != null ? systemQty : BigDecimal.ZERO);
        item.setCountedQty(countedQty);
        item.setLineNotes(lineNotes);
        if (countedQty != null) {
            item.setDifference(countedQty.subtract(systemQty != null ? systemQty : BigDecimal.ZERO));
        }
        count.addItem(item);
    }

    private StockCount find(Long id) {
        return counts.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Conteo " + id + " no encontrado"));
    }

    private static StockCountDtos.Response toResponse(StockCount c) {
        int discrepancies = 0;
        int adjustedQty = 0;
        var items = c.getItems().stream().map(i -> {
            Product p = i.getProduct();
            return new StockCountDtos.ItemResponse(
                    i.getId(), p != null ? p.getId() : null, p != null ? p.getName() : null,
                    p != null ? p.getSku() : null, p != null ? p.getUnit() : null,
                    p != null && p.getCategory() != null ? p.getCategory().getName() : null,
                    p != null ? (p.getAvgCost() != null ? p.getAvgCost() : p.getCost()) : null,
                    i.getSystemQty(), i.getCountedQty(), i.getDifference(), i.getLineNotes());
        }).toList();
        for (StockCountItem i : c.getItems()) {
            if (i.getDifference() != null && i.getDifference().compareTo(BigDecimal.ZERO) != 0) {
                discrepancies++;
                adjustedQty += i.getDifference().abs().intValue();
            }
        }
        return new StockCountDtos.Response(c.getId(), c.getDocNumber(),
                c.getBranch() != null ? c.getBranch().getId() : null,
                c.getBranch() != null ? c.getBranch().getName() : null,
                c.getCountDate(), c.getResponsible(), c.getCategory(), c.getCategoryLabel(),
                c.getStatus(), c.getNotes(), discrepancies, adjustedQty, items);
    }
}
