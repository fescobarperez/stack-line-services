package com.erp_maya.purchasing.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.inventory.repository.ProductStockRepository;
import com.erp_maya.inventory.domain.ProductStock;
import com.erp_maya.inventory.service.StockService;
import com.erp_maya.partner.repository.SupplierRepository;
import com.erp_maya.purchasing.domain.PurchaseOrder;
import com.erp_maya.purchasing.domain.PurchaseOrderItem;
import com.erp_maya.purchasing.dto.PurchaseOrderDtos;
import com.erp_maya.purchasing.repository.PurchaseOrderRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class PurchaseOrderService {

    private final PurchaseOrderRepository orders;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final BranchRepository branches;
    private final StockService stockService;
    private final ProductStockRepository stock;
    private final TenantContext tenant;

    public PurchaseOrderService(PurchaseOrderRepository orders, ProductRepository products,
                                SupplierRepository suppliers, BranchRepository branches,
                                StockService stockService, ProductStockRepository stock,
                                TenantContext tenant) {
        this.stock = stock;
        this.orders = orders;
        this.products = products;
        this.suppliers = suppliers;
        this.branches = branches;
        this.stockService = stockService;
        this.tenant = tenant;
    }

    @Transactional
    public Page<PurchaseOrderDtos.Response> list(Pageable pageable) {
        return orders.findByCompanyIdOrderByOrderDateDesc(tenant.getCompanyId(), pageable)
                .map(PurchaseOrderService::toResponse);
    }

    @Transactional
    public PurchaseOrderDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public PurchaseOrderDtos.Response create(PurchaseOrderDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        PurchaseOrder po = new PurchaseOrder();
        po.setCompanyId(companyId);
        po.setDocNumber(req.docNumber() != null && !req.docNumber().isBlank()
                ? req.docNumber() : "OC-" + Instant.now().toEpochMilli());
        po.setOrderDate(req.orderDate());
        po.setNotes(req.notes());
        // Imputar la OC a un proyecto la cuenta como comprometido hasta que
        // se facture; la factura es la que pasa a ejecutado.
        po.setProjectId(req.projectId());
        po.setStatus("pending");
        if (req.supplierId() != null) {
            suppliers.findByIdAndCompanyId(req.supplierId(), companyId).ifPresent(po::setSupplier);
        }
        if (req.branchId() != null) {
            branches.findByIdAndCompanyId(req.branchId(), companyId).ifPresent(po::setBranch);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderDtos.ItemRequest ir : req.items()) {
            Product product = products.findByIdAndCompanyId(ir.productId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto " + ir.productId() + " no encontrado"));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProduct(product);
            item.setQtyOrdered(ir.qtyOrdered());
            item.setQtyReceived(BigDecimal.ZERO);
            item.setUnitCost(ir.unitCost());
            po.addItem(item);
            total = total.add(ir.qtyOrdered().multiply(ir.unitCost()));
        }
        po.setTotal(total);
        return toResponse(orders.save(po));
    }

    /**
     * Cancela una orden pendiente. Sólo se permite si no tiene recepciones: una vez
     * que entró mercancía al kardex, revertirla es una devolución, no una cancelación.
     */
    @Transactional
    public PurchaseOrderDtos.Response cancel(Long id) {
        PurchaseOrder po = find(id);
        if ("cancelled".equals(po.getStatus())) {
            throw new IllegalStateException("La orden ya está cancelada.");
        }
        if ("received".equals(po.getStatus())) {
            throw new IllegalStateException("La orden ya fue recibida por completo; no puede cancelarse.");
        }
        boolean anyReceived = po.getItems().stream()
                .anyMatch(i -> i.getQtyReceived().compareTo(BigDecimal.ZERO) > 0);
        if (anyReceived) {
            throw new IllegalStateException(
                    "La orden tiene mercancía recibida; registre una devolución en lugar de cancelarla.");
        }
        po.setStatus("cancelled");
        return toResponse(orders.update(po));
    }

    /** Recibe la orden (total o parcial): entra stock al kardex y actualiza costos y estado. */
    @Transactional
    public PurchaseOrderDtos.Response receive(Long id, PurchaseOrderDtos.ReceiveRequest req) {
        PurchaseOrder po = find(id);
        Map<Long, BigDecimal> requested = new HashMap<>();
        if (req != null && req.items() != null) {
            for (PurchaseOrderDtos.ReceiptItem ri : req.items()) {
                requested.put(ri.itemId(), ri.quantity());
            }
        }

        for (PurchaseOrderItem item : po.getItems()) {
            BigDecimal pending = item.getQtyOrdered().subtract(item.getQtyReceived());
            BigDecimal delta = requested.isEmpty() ? pending : requested.getOrDefault(item.getId(), BigDecimal.ZERO);
            if (delta.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // No se puede recibir más de lo ordenado: entraría stock fantasma al kardex.
            if (delta.compareTo(pending) > 0) {
                throw new IllegalStateException("El renglón " + item.getId() + " sólo tiene " + pending
                        + " unidad(es) pendiente(s); no se pueden recibir " + delta + ".");
            }
            // La orden se captura como viene la factura del proveedor —1 paquete
            // a Q25— pero la bodega guarda tornillos. Aquí se traduce: la
            // cantidad se multiplica por el factor y el costo se divide entre él.
            Product product = item.getProduct();
            BigDecimal factor = product != null ? product.purchaseFactorOrOne() : BigDecimal.ONE;
            BigDecimal qtyStock = delta.multiply(factor);
            BigDecimal costPerStockUnit = item.getUnitCost()
                    .divide(factor, 6, RoundingMode.HALF_UP);

            // El promedio se calcula ANTES de mover el kardex: necesita la
            // existencia previa a esta recepción.
            if (product != null) {
                product.setAvgCost(newAverageCost(product, qtyStock, costPerStockUnit));
            }
            stockService.applyMovement(product, po.getBranch(), null, "reception",
                    qtyStock, "purchase", po.getDocNumber(), "");
            item.setQtyReceived(item.getQtyReceived().add(delta));
        }

        boolean allReceived = po.getItems().stream()
                .allMatch(i -> i.getQtyReceived().compareTo(i.getQtyOrdered()) >= 0);
        boolean anyReceived = po.getItems().stream()
                .anyMatch(i -> i.getQtyReceived().compareTo(BigDecimal.ZERO) > 0);
        po.setStatus(allReceived ? "received" : anyReceived ? "partial" : po.getStatus());
        return toResponse(orders.update(po));
    }

    /**
     * Promedio móvil ponderado sobre la existencia previa.
     *
     * Antes esto era `setAvgCost(unitCost)`: el ÚLTIMO costo, sobrescrito en
     * cada compra. Con eso, comprar 100 tornillos a Q0.25 y después 100 a
     * Q0.30 dejaba los 164 restantes valuados a Q0.30 y le cargaba de más a
     * cada proyecto que los consumiera. Es la cifra con la que
     * ProjectService descarga el consumo de materia prima.
     *
     * Sin existencia previa —o con existencia negativa por un descuadre— el
     * promedio es simplemente el costo de esta compra: promediar contra una
     * base que no existe daría un número inventado.
     */
    private BigDecimal newAverageCost(Product product, BigDecimal qtyIn, BigDecimal costIn) {
        BigDecimal onHand = stock.findByCompanyIdAndProductId(tenant.getCompanyId(), product.getId())
                .stream().map(ProductStock::getQuantity)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prevAvg = product.getAvgCost();
        if (onHand.signum() <= 0 || prevAvg == null || prevAvg.signum() <= 0) {
            return costIn.setScale(4, RoundingMode.HALF_UP);
        }
        return onHand.multiply(prevAvg).add(qtyIn.multiply(costIn))
                .divide(onHand.add(qtyIn), 4, RoundingMode.HALF_UP);
    }

    private PurchaseOrder find(Long id) {
        return orders.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra " + id + " no encontrada"));
    }

    private static PurchaseOrderDtos.Response toResponse(PurchaseOrder po) {
        var items = po.getItems().stream().map(i -> new PurchaseOrderDtos.ItemResponse(
                i.getId(), i.getProduct() != null ? i.getProduct().getId() : null,
                i.getProduct() != null ? i.getProduct().getName() : null,
                i.getQtyOrdered(), i.getQtyReceived(), i.getUnitCost())).toList();
        return new PurchaseOrderDtos.Response(po.getId(), po.getDocNumber(),
                po.getSupplier() != null ? po.getSupplier().getId() : null,
                po.getSupplier() != null ? po.getSupplier().getName() : null,
                po.getBranch() != null ? po.getBranch().getId() : null,
                po.getBranch() != null ? po.getBranch().getName() : null,
                po.getProjectId(),
                po.getOrderDate(), po.getTotal(), po.getStatus(), po.getNotes(), items);
    }
}
