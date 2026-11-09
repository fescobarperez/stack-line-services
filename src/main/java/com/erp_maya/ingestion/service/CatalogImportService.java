package com.erp_maya.ingestion.service;

import com.erp_maya.catalog.domain.Category;
import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.domain.ProductSupplier;
import com.erp_maya.catalog.dto.ProductRequest;
import com.erp_maya.catalog.dto.ProductSupplierDtos;
import com.erp_maya.catalog.repository.CategoryRepository;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.catalog.repository.ProductSupplierRepository;
import com.erp_maya.catalog.service.ProductService;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.ingestion.domain.CatalogImportBatch;
import com.erp_maya.ingestion.domain.CatalogImportItem;
import com.erp_maya.ingestion.domain.SupplierCatalogItem;
import com.erp_maya.ingestion.dto.CatalogImportDtos;
import com.erp_maya.ingestion.repository.CatalogImportBatchRepository;
import com.erp_maya.ingestion.repository.CatalogImportItemRepository;
import com.erp_maya.ingestion.repository.SupplierCatalogItemRepository;
import com.erp_maya.partner.repository.SupplierRepository;
import com.erp_maya.partner.repository.SupplierWebsiteRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Singleton
public class CatalogImportService {

    private final CatalogImportBatchRepository batches;
    private final CatalogImportItemRepository items;
    private final SupplierRepository suppliers;
    private final SupplierWebsiteRepository websites;
    private final CatalogCandidateService candidates;
    private final ProductService productService;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductSupplierRepository productSuppliers;
    private final SupplierCatalogItemRepository catalogItems;
    private final TenantContext tenant;

    public CatalogImportService(CatalogImportBatchRepository batches,
                                CatalogImportItemRepository items,
                                SupplierRepository suppliers,
                                SupplierWebsiteRepository websites,
                                CatalogCandidateService candidates,
                                ProductService productService,
                                CategoryRepository categories,
                                ProductRepository products,
                                ProductSupplierRepository productSuppliers,
                                SupplierCatalogItemRepository catalogItems,
                                TenantContext tenant) {
        this.batches = batches;
        this.items = items;
        this.suppliers = suppliers;
        this.websites = websites;
        this.candidates = candidates;
        this.productService = productService;
        this.categories = categories;
        this.products = products;
        this.productSuppliers = productSuppliers;
        this.catalogItems = catalogItems;
        this.tenant = tenant;
    }

    @Transactional
    public CatalogImportDtos.BatchResponse preview(CatalogImportDtos.PreviewRequest request) {
        return previewForCompany(tenant.getCompanyId(), request);
    }

    /** Variante para el worker del servicio Python, sin depender del TenantContext HTTP. */
    @Transactional
    public CatalogImportDtos.BatchResponse previewForCompany(Long companyId, CatalogImportDtos.PreviewRequest request) {
        String sourceType = request.sourceType().trim().toLowerCase(Locale.ROOT);
        if (!List.of("csv", "crawl", "mcp").contains(sourceType)) {
            throw new IllegalStateException("El origen debe ser csv, crawl o mcp");
        }
        suppliers.findByIdAndCompanyId(request.supplierId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + request.supplierId() + " no encontrado"));
        if (request.supplierWebsiteId() != null) {
            websites.findByIdAndCompanyId(request.supplierWebsiteId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sitio del proveedor no encontrado"));
        }
        CatalogImportBatch batch = new CatalogImportBatch();
        batch.setCompanyId(companyId);
        batch.setSupplierId(request.supplierId());
        batch.setSupplierWebsiteId(request.supplierWebsiteId());
        batch.setSourceType(sourceType);
        batch.setSourceReference(request.sourceReference());
        batch.setCrawlRunId(request.crawlRunId());
        batch.setCategoryScope(toScope(request.categoryScope()));
        batch.setStatus("preview");
        CatalogImportBatch saved = batches.save(batch);
        for (int index = 0; index < request.items().size(); index++) {
            CatalogImportDtos.Row row = request.items().get(index);
            CatalogCandidateService.Decision decision = candidates.decide(companyId, row.name());
            CatalogImportItem item = new CatalogImportItem();
            item.setCompanyId(companyId);
            item.setBatchId(saved.getId());
            item.setRowNumber(index + 1);
            item.setRawPayload(toPayload(row));
            item.setNormalizedPayload(normalizedPayload(row));
            item.setAction(decision.action());
            item.setTargetProductId(decision.targetProductId());
            item.setConfidence(decision.confidence());
            item.setDecisionReason(decision.reason());
            item.setEvidence(decision.evidence());
            item.setAlternativeCandidates(decision.alternatives());
            item.setStatus("pending");
            items.save(item);
        }
        return toResponse(saved, items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(companyId, saved.getId()), companyId);
    }

    @Transactional
    public CatalogImportDtos.BatchResponse saveCategorySelection(Long supplierWebsiteId,
                                                                   List<CatalogImportDtos.CategoryScopeItem> scope,
                                                                   List<CatalogImportDtos.CategoryTreeItem> tree) {
        Long companyId = tenant.getCompanyId();
        var website = websites.findByIdAndCompanyId(supplierWebsiteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier website not found"));
        if (scope == null || scope.isEmpty() || tree == null || tree.isEmpty()) {
            throw new IllegalStateException("A category selection must include at least one selected category and a category tree");
        }
        CatalogImportBatch batch = batches
                .findFirstByCompanyIdAndSupplierWebsiteIdAndSourceTypeAndStatusOrderByCreatedAtDesc(
                        companyId, supplierWebsiteId, "crawl", "category_selection")
                .orElseGet(CatalogImportBatch::new);
        batch.setCompanyId(companyId);
        batch.setSupplierId(website.getSupplierId());
        batch.setSupplierWebsiteId(supplierWebsiteId);
        batch.setSourceType("crawl");
        batch.setSourceReference(website.getBaseUrl());
        batch.setCategoryScope(toScope(scope));
        batch.setCategoryTree(toTree(tree));
        batch.setStatus("category_selection");
        CatalogImportBatch saved = batches.save(batch);
        return toResponse(saved, List.of(), companyId);
    }

    @Transactional
    public List<CatalogImportDtos.PendingBatchResponse> listPending(Long supplierWebsiteId) {
        Long companyId = tenant.getCompanyId();
        List<CatalogImportBatch> pending = new ArrayList<>(batches
                .findByCompanyIdAndSupplierWebsiteIdAndSourceTypeAndStatusOrderByCreatedAtDesc(
                        companyId, supplierWebsiteId, "crawl", "preview"));
        pending.addAll(batches.findByCompanyIdAndSupplierWebsiteIdAndSourceTypeAndStatusOrderByCreatedAtDesc(
                companyId, supplierWebsiteId, "crawl", "category_selection"));
        return pending.stream()
                .map(batch -> {
                    int itemCount = (int) items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(companyId, batch.getId())
                            .stream()
                            .filter(item -> !"rejected".equals(item.getStatus()))
                            .count();
                    return new CatalogImportDtos.PendingBatchResponse(
                            batch.getId(), batch.getSupplierWebsiteId(), batch.getCrawlRunId(),
                            batch.getSourceReference(), batch.getStatus(), itemCount,
                            toScopeResponse(batch.getCategoryScope()), toTreeResponse(batch.getCategoryTree(), batch.getCategoryScope()));
                })
                .toList();
    }

    @Transactional
    public CatalogImportDtos.BatchResponse get(Long id) {
        Long companyId = tenant.getCompanyId();
        CatalogImportBatch batch = batches.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importación " + id + " no encontrado"));
        return toResponse(batch, items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(companyId, id));
    }

    @Transactional
    public void delete(Long id) {
        CatalogImportBatch batch = findBatch(id);
        if (!"preview".equals(batch.getStatus()) && !"category_selection".equals(batch.getStatus())) {
            throw new IllegalStateException("Only pending catalog proposals or category selections can be deleted");
        }
        // catalog_import_items tiene ON DELETE CASCADE sobre batch_id.
        batches.delete(batch);
    }

    @Transactional
    public CatalogImportDtos.BatchResponse excludeItem(Long batchId, Long itemId) {
        CatalogImportBatch batch = findBatch(batchId);
        CatalogImportItem item = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Fila de importación " + itemId + " no encontrada"));
        if (!tenant.getCompanyId().equals(item.getCompanyId()) || !batchId.equals(item.getBatchId())) {
            throw new ResourceNotFoundException("Fila de importación " + itemId + " no encontrada");
        }
        if ("applied".equals(batch.getStatus())) {
            throw new IllegalStateException("No se puede excluir una fila de un lote ya aplicado");
        }
        item.setAction("OMIT");
        item.setStatus("rejected");
        item.setDecisionReason("Fila excluida manualmente de esta carga");
        item.setEvidence(Map.of("source", "user", "method", "exclude_from_batch"));
        items.save(item);
        return get(batchId);
    }

    @Transactional
    public CatalogImportDtos.BatchResponse updateDecision(Long batchId, Long itemId,
                                                           CatalogImportDtos.DecisionRequest request) {
        CatalogImportBatch batch = findBatch(batchId);
        CatalogImportItem item = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Fila de importación " + itemId + " no encontrada"));
        if (!tenant.getCompanyId().equals(item.getCompanyId()) || !batchId.equals(item.getBatchId())) {
            throw new ResourceNotFoundException("Fila de importación " + itemId + " no encontrada");
        }
        applyDecision(batch, item, request.action(), request.targetProductId());
        items.save(item);
        return get(batchId);
    }

    @Transactional
    public CatalogImportDtos.BatchResponse saveProgress(Long batchId,
                                                         CatalogImportDtos.ProgressRequest request) {
        CatalogImportBatch batch = findBatch(batchId);
        List<CatalogImportItem> rows = items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(
                tenant.getCompanyId(), batchId);
        Map<Long, CatalogImportItem> byId = new LinkedHashMap<>();
        rows.forEach(item -> byId.put(item.getId(), item));
        List<CatalogImportDtos.ProgressDecision> decisions = request == null || request.decisions() == null
                ? List.of() : request.decisions();
        for (CatalogImportDtos.ProgressDecision decision : decisions) {
            CatalogImportItem item = byId.get(decision.itemId());
            if (item == null) {
                throw new ResourceNotFoundException("Fila de importación " + decision.itemId() + " no encontrada");
            }
            applyDecision(batch, item, decision.action(), decision.targetProductId());
            items.save(item);
        }
        return get(batchId);
    }

    private void applyDecision(CatalogImportBatch batch, CatalogImportItem item,
                               String requestedAction, Long targetProductId) {
        if (!"preview".equals(batch.getStatus()) || !"pending".equals(item.getStatus())) {
            throw new IllegalStateException("Solo se pueden editar decisiones pendientes de un lote en preview");
        }
        String action = requestedAction.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CREATE", "LINK_EXISTING_PRODUCT", "UPDATE_OFFER", "UPDATE_PRODUCT", "REVIEW", "OMIT")
                .contains(action)) {
            throw new IllegalStateException("Acción de importación no válida: " + action);
        }
        if (List.of("LINK_EXISTING_PRODUCT", "UPDATE_OFFER", "UPDATE_PRODUCT").contains(action)) {
            if (targetProductId == null) {
                throw new IllegalStateException("La acción " + action + " necesita un producto candidato");
            }
            productService.get(targetProductId);
        }
        if ("CREATE".equals(action) || "OMIT".equals(action) || "REVIEW".equals(action)) {
            item.setTargetProductId(null);
        } else {
            item.setTargetProductId(targetProductId);
        }
        item.setAction(action);
        item.setDecisionReason("Decisión ajustada manualmente desde la pre-autorización");
        item.setEvidence(Map.of("source", "user", "method", "manual_override"));
    }

    @Transactional
    public CatalogImportDtos.BatchResponse approve(Long id, CatalogImportDtos.SelectionRequest request) {
        CatalogImportBatch batch = findBatch(id);
        List<CatalogImportItem> pending = items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(
                tenant.getCompanyId(), id).stream()
                .filter(item -> "pending".equals(item.getStatus()))
                .toList();
        if (pending.isEmpty()) {
            throw new IllegalStateException("El lote no tiene filas pendientes para aprobar");
        }
        List<CatalogImportItem> pendingReview = pending.stream()
                .filter(item -> "REVIEW".equals(item.getAction()))
                .toList();
        if (!pendingReview.isEmpty()) {
            throw new IllegalStateException("Hay " + pendingReview.size()
                    + " filas REVIEW pendientes; vincúlalas, créalas o exclúyelas antes de aprobar");
        }
        List<CatalogImportItem> selected = selectedRows(batch, request);
        for (CatalogImportItem item : selected) {
            item.setStatus("selected");
            items.save(item);
        }
        batch.setStatus("approved");
        batches.update(batch);
        return get(id);
    }

    @Transactional
    public CatalogImportDtos.BatchResponse reject(Long id, CatalogImportDtos.SelectionRequest request) {
        CatalogImportBatch batch = findBatch(id);
        List<CatalogImportItem> selected = selectedRows(batch, request);
        if (selected.isEmpty()) {
            throw new IllegalStateException("El lote no tiene filas pendientes para rechazar");
        }
        for (CatalogImportItem item : selected) {
            item.setStatus("rejected");
            items.save(item);
        }
        boolean quedanPendientes = items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(
                tenant.getCompanyId(), id).stream().anyMatch(item -> "pending".equals(item.getStatus()));
        if (!quedanPendientes) {
            batch.setStatus("rejected");
            batches.update(batch);
        }
        return get(id);
    }

    @Transactional
    public CatalogImportDtos.BatchResponse apply(Long id) {
        CatalogImportBatch batch = findBatch(id);
        if ("applied".equals(batch.getStatus())) return get(id);
        if (!"approved".equals(batch.getStatus())) {
            throw new IllegalStateException("El lote debe estar aprobado antes de aplicarse");
        }
        List<CatalogImportItem> selected = items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(
                        tenant.getCompanyId(), id).stream()
                .filter(item -> "selected".equals(item.getStatus()))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("El lote aprobado no tiene filas seleccionadas");
        }
        if (batch.getSupplierWebsiteId() == null) {
            throw new IllegalStateException("El lote necesita un sitio web para conservar el origen del proveedor");
        }
        for (CatalogImportItem item : selected) {
            applyItem(batch, item);
        }
        batch.setStatus("applied");
        batch.setAppliedAt(java.time.Instant.now());
        batches.update(batch);
        return get(id);
    }

    private void applyItem(CatalogImportBatch batch, CatalogImportItem item) {
        if ("OMIT".equals(item.getAction())) {
            item.setStatus("applied");
            items.save(item);
            return;
        }
        if ("REVIEW".equals(item.getAction()) || "UPDATE_PRODUCT".equals(item.getAction())) {
            throw new IllegalStateException("La fila " + item.getRowNumber()
                    + " todavía necesita una decisión explícita antes de aplicarse");
        }

        Map<String, Object> data = item.getNormalizedPayload();
        String name = text(data, "name");
        BigDecimal cost = decimal(data, "unitCost");
        if (name.isBlank()) throw new IllegalStateException("La fila " + item.getRowNumber() + " no tiene nombre");
        if (cost == null || cost.signum() <= 0) {
            throw new IllegalStateException("La fila " + item.getRowNumber() + " necesita un costo positivo");
        }

        Long productId = item.getTargetProductId();
        if ("CREATE".equals(item.getAction())) {
            String sku = "IMP-" + batch.getSupplierId() + "-" + batch.getId() + "-" + item.getRowNumber();
            var created = productService.create(new ProductRequest(
                    sku, name, null, BigDecimal.ZERO, cost, defaultText(data, "unit", "unid"),
                    null, BigDecimal.ONE, "raw_material", Boolean.TRUE, null, "active",
                    batch.getSupplierId(), cost));
            productId = created.id();
        } else if (productId == null) {
            throw new IllegalStateException("La fila " + item.getRowNumber() + " no tiene producto candidato");
        }

        if ("LINK_EXISTING_PRODUCT".equals(item.getAction()) || "UPDATE_OFFER".equals(item.getAction())) {
            linkOffer(productId, batch.getSupplierId(), cost);
        }
        item.setTargetProductId(productId);
        persistSourceItem(batch, item, productId, data);
        item.setStatus("applied");
        items.save(item);
    }

    private void linkOffer(Long productId, Long supplierId, BigDecimal cost) {
        Long companyId = tenant.getCompanyId();
        ProductSupplier existing = productSuppliers
                .findByCompanyIdAndProductIdAndSupplierId(companyId, productId, supplierId).orElse(null);
        if (existing == null) {
            productService.addSupplier(productId, new ProductSupplierDtos.Request(supplierId, cost, null));
        } else {
            productService.updateSupplier(productId, supplierId, new ProductSupplierDtos.Update(cost, null));
        }
    }

    private void persistSourceItem(CatalogImportBatch batch, CatalogImportItem item,
                                   Long productId, Map<String, Object> data) {
        SupplierCatalogItem source = new SupplierCatalogItem();
        source.setCompanyId(tenant.getCompanyId());
        source.setSupplierId(batch.getSupplierId());
        source.setSupplierWebsiteId(batch.getSupplierWebsiteId());
        source.setProductId(productId);
        source.setSourceProductName(text(data, "name"));
        source.setSourceCategoryPath(strings(data.get("categoryPath")));
        source.setSourceListingUrl(textOrNull(data, "sourceUrl"));
        source.setProductUrl(textOrNull(data, "productUrl"));
        source.setSupplierSku(textOrNull(data, "supplierSku"));
        source.setBrand(textOrNull(data, "brand"));
        source.setManufacturerName(textOrNull(data, "manufacturerName"));
        source.setManufacturerPartNumber(textOrNull(data, "manufacturerPartNumber"));
        source.setGtin(textOrNull(data, "gtin"));
        source.setSourceAttributes(map(data.get("attributes")));
        source.setObservedUnit(textOrNull(data, "unit"));
        source.setObservedPackSize(decimal(data, "packSize"));
        source.setObservedCurrency(defaultText(data, "currency", "GTQ"));
        source.setObservedUnitCost(decimal(data, "unitCost"));
        source.setActive(true);
        catalogItems.save(source);
    }

    private CatalogImportBatch findBatch(Long id) {
        return batches.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importación " + id + " no encontrado"));
    }

    private List<CatalogImportItem> selectedRows(CatalogImportBatch batch,
                                                  CatalogImportDtos.SelectionRequest request) {
        List<CatalogImportItem> rows = items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(
                tenant.getCompanyId(), batch.getId());
        List<Long> ids = request == null ? List.of() : request.itemIds();
        return rows.stream()
                .filter(item -> "pending".equals(item.getStatus()))
                .filter(item -> ids == null || ids.isEmpty() || ids.contains(item.getId()))
                .toList();
    }

    private static String text(Map<String, Object> data, String key) {
        return textOrNull(data, key) == null ? "" : textOrNull(data, key);
    }

    private List<String> currentCategoryPath(CatalogImportItem item, Long companyId) {
        if (item.getTargetProductId() == null) return List.of();
        Product product = products.findByIdAndCompanyId(item.getTargetProductId(), companyId).orElse(null);
        if (product == null || product.getCategory() == null) return List.of();
        List<String> path = new ArrayList<>();
        Long categoryId = product.getCategory().getId();
        int guard = 0;
        while (categoryId != null && guard++ < 50) {
            Category category = categories.findByIdAndCompanyId(categoryId, companyId).orElse(null);
            if (category == null) break;
            path.add(0, category.getName());
            categoryId = category.getParentId();
        }
        return path;
    }

    private static String textOrNull(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        return value == null ? null : value.toString().trim();
    }

    private static String defaultText(Map<String, Object> data, String key, String fallback) {
        String value = textOrNull(data, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static BigDecimal decimal(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static List<Map<String, Object>> toScope(List<CatalogImportDtos.CategoryScopeItem> scope) {
        if (scope == null || scope.isEmpty()) return null;
        return scope.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", item.id());
            value.put("url", item.url());
            value.put("path", item.path() == null ? List.of() : item.path());
            return value;
        }).toList();
    }

    private static List<Map<String, Object>> toTree(List<CatalogImportDtos.CategoryTreeItem> tree) {
        return tree.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", item.id());
            value.put("name", item.name());
            value.put("url", item.url());
            value.put("parentId", item.parentId());
            return value;
        }).toList();
    }

    private static String categoryName(Map<String, Object> item) {
        String name = text(item, "name");
        String url = text(item, "url");
        if (!name.isBlank() && !name.equalsIgnoreCase("Lista de precios GTQ predeterminada")
                && !name.startsWith("/shop/category/")) return name;
        String path = url.split("\\?", 2)[0];
        int slash = path.lastIndexOf('/');
        String slug = slash >= 0 ? path.substring(slash + 1) : path;
        slug = slug.replaceFirst("-\\d+$", "");
        return java.util.Arrays.stream(slug.split("-"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static List<CatalogImportDtos.CategoryTreeItem> toTreeResponse(List<Map<String, Object>> tree,
                                                                            List<Map<String, Object>> scope) {
        if (tree != null && !tree.isEmpty()) {
            return tree.stream().map(item -> new CatalogImportDtos.CategoryTreeItem(
                    text(item, "id"), categoryName(item), text(item, "url"), text(item, "parentId"))).toList();
        }
        if (scope == null || scope.isEmpty()) return List.of();
        List<List<String>> paths = scope.stream().map(item -> strings(item.get("path"))).toList();
        return scope.stream().map(item -> {
            List<String> path = strings(item.get("path"));
            String parentId = null;
            if (path.size() > 1) {
                List<String> parentPath = path.subList(0, path.size() - 1);
                for (int index = 0; index < scope.size(); index++) {
                    if (paths.get(index).equals(parentPath)) {
                        parentId = text(scope.get(index), "id");
                        break;
                    }
                }
            }
            return new CatalogImportDtos.CategoryTreeItem(
                    text(item, "id"), path.isEmpty() ? text(item, "id") : path.get(path.size() - 1),
                    text(item, "url"), parentId);
        }).toList();
    }

    private static List<CatalogImportDtos.CategoryScopeItem> toScopeResponse(List<Map<String, Object>> scope) {
        if (scope == null) return List.of();
        return scope.stream().map(item -> new CatalogImportDtos.CategoryScopeItem(
                text(item, "id"), text(item, "url"), strings(item.get("path")))).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }
    @Transactional
    public List<CatalogImportDtos.BatchResponse> list() {
        Long companyId = tenant.getCompanyId();
        return batches.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(batch -> toResponse(batch,
                        items.findByCompanyIdAndBatchIdOrderByRowNumberAsc(companyId, batch.getId())))
                .toList();
    }

    private static Map<String, Object> toPayload(CatalogImportDtos.Row row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", row.name());
        payload.put("unitCost", row.unitCost());
        payload.put("currency", row.currency());
        payload.put("sourceUrl", row.sourceUrl());
        payload.put("productUrl", row.productUrl());
        payload.put("categoryPath", row.categoryPath());
        payload.put("supplierSku", row.supplierSku());
        payload.put("brand", row.brand());
        payload.put("manufacturerName", row.manufacturerName());
        payload.put("manufacturerPartNumber", row.manufacturerPartNumber());
        payload.put("gtin", row.gtin());
        payload.put("unit", row.unit());
        payload.put("packSize", row.packSize());
        payload.put("description", row.description());
        payload.put("attributes", row.attributes());
        return payload;
    }

    private static Map<String, Object> normalizedPayload(CatalogImportDtos.Row row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("name", row.name().trim());
        normalized.put("unitCost", row.unitCost());
        normalized.put("currency", row.currency() == null || row.currency().isBlank()
                ? "GTQ" : row.currency().trim().toUpperCase(Locale.ROOT));
        normalized.put("sourceUrl", row.sourceUrl());
        normalized.put("productUrl", row.productUrl());
        normalized.put("categoryPath", row.categoryPath() == null ? List.of() : row.categoryPath());
        normalized.put("supplierSku", row.supplierSku());
        normalized.put("brand", row.brand());
        normalized.put("manufacturerName", row.manufacturerName());
        normalized.put("manufacturerPartNumber", row.manufacturerPartNumber());
        normalized.put("gtin", row.gtin());
        normalized.put("unit", row.unit());
        normalized.put("packSize", row.packSize());
        normalized.put("description", row.description());
        normalized.put("attributes", row.attributes() == null ? Map.of() : row.attributes());
        return normalized;
    }

    private CatalogImportDtos.BatchResponse toResponse(CatalogImportBatch batch,
                                                        List<CatalogImportItem> rows) {
        return toResponse(batch, rows, tenant.getCompanyId());
    }

    private CatalogImportDtos.BatchResponse toResponse(CatalogImportBatch batch,
                                                        List<CatalogImportItem> rows,
                                                        Long companyId) {
        List<CatalogImportDtos.ItemResponse> responseRows = rows.stream()
                .filter(row -> !"rejected".equals(row.getStatus()))
                .sorted(Comparator
                        .comparing((CatalogImportItem row) -> !"REVIEW".equals(row.getAction()))
                        .thenComparing(CatalogImportItem::getRowNumber))
                .map(row -> {
            List<String> currentCategoryPath = currentCategoryPath(row, companyId);
            List<String> proposedCategoryPath = strings(row.getNormalizedPayload().get("categoryPath"));
            BigDecimal currentCost = row.getTargetProductId() == null ? null
                    : productSuppliers.findByCompanyIdAndProductIdAndSupplierId(
                            companyId, row.getTargetProductId(), batch.getSupplierId())
                            .map(ProductSupplier::getUnitCost).orElse(null);
            BigDecimal proposedCost = decimal(row.getNormalizedPayload(), "unitCost");
            return new CatalogImportDtos.ItemResponse(row.getId(), row.getRowNumber(),
                    text(row.getNormalizedPayload(), "name"), row.getAction(), row.getTargetProductId(),
                    currentCategoryPath, proposedCategoryPath, currentCost, proposedCost,
                    row.getConfidence(), row.getDecisionReason(),
                    row.getEvidence(), row.getAlternativeCandidates(), row.getStatus());
        }).toList();
        return new CatalogImportDtos.BatchResponse(batch.getId(), batch.getSupplierId(),
                batch.getSupplierWebsiteId(), batch.getCrawlRunId(), batch.getSourceType(),
                batch.getSourceReference(), batch.getStatus(), responseRows.size(),
                toScopeResponse(batch.getCategoryScope()), toTreeResponse(batch.getCategoryTree(), batch.getCategoryScope()), responseRows);
    }
}
