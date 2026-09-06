package com.erp_maya.inventory.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.inventory.domain.ProductStock;
import com.erp_maya.inventory.domain.StockMovement;
import com.erp_maya.inventory.dto.StockDtos;
import com.erp_maya.inventory.repository.ProductStockRepository;
import com.erp_maya.inventory.repository.StockMovementRepository;
import com.erp_maya.security.domain.User;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Existencias y kardex. applyMovement es el punto único que mueve stock. */
@Singleton
public class StockService {

    private final ProductStockRepository stock;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final TenantContext tenant;

    public StockService(ProductStockRepository stock, StockMovementRepository movements,
                        ProductRepository products, BranchRepository branches, TenantContext tenant) {
        this.stock = stock;
        this.movements = movements;
        this.products = products;
        this.branches = branches;
        this.tenant = tenant;
    }

    @Transactional
    public List<StockDtos.StockRow> listStock(Long branchId, Long productId) {
        Long companyId = tenant.getCompanyId();
        List<ProductStock> rows;
        if (branchId != null) {
            rows = stock.findByCompanyIdAndBranchId(companyId, branchId);
        } else if (productId != null) {
            rows = stock.findByCompanyIdAndProductId(companyId, productId);
        } else {
            rows = stock.findByCompanyId(companyId);
        }
        return rows.stream().map(StockService::toStockRow).toList();
    }

    @Transactional
    public Page<StockDtos.Movement> listMovements(Long productId, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<StockMovement> page = (productId != null)
                ? movements.findByCompanyIdAndProductIdOrderByCreatedAtDesc(companyId, productId, pageable)
                : movements.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
        return page.map(StockService::toMovement);
    }

    @Transactional
    public StockDtos.Movement adjust(StockDtos.AdjustmentRequest req) {
        Product product = products.findByIdAndCompanyId(req.productId(), tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + req.productId() + " no encontrado"));
        Branch branch = branches.findByIdAndCompanyId(req.branchId(), tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal " + req.branchId() + " no encontrada"));
        // Un ajuste es una acción deliberada del usuario sobre una existencia:
        // si el producto no lleva, es un error que hay que decir, no ignorar.
        if (!tracksStock(product)) {
            throw new IllegalStateException(
                    product.getName() + " no lleva existencias: no se le puede ajustar inventario");
        }
        StockMovement m = applyMovement(product, branch, null, "adjustment", req.quantity(),
                "adjustment", req.note(), req.batch());
        return toMovement(m);
    }

    /**
     * ¿Este producto lleva existencias? Un servicio —mano de obra, un anticipo
     * facturado— no tiene kardex ni bodega. `tracks_stock` llegó en la 039 pero
     * nadie lo miraba: hasta ahora vender un servicio descontaba existencias de
     * algo que no existe.
     *
     * Nulo se trata como que SÍ lleva: la columna es NOT NULL DEFAULT TRUE, y
     * ante la duda es mejor mover el kardex de más que perder un movimiento.
     */
    private static boolean tracksStock(Product p) {
        return !Boolean.FALSE.equals(p.getTracksStock());
    }

    /**
     * Registra un movimiento en el kardex y actualiza la existencia de la
     * (producto, sucursal, lote). qty con signo: negativo descuenta.
     * Reutilizado por POS, compras, traslados y toma física.
     *
     * Devuelve null si el producto no lleva existencias: no hay movimiento que
     * registrar. Quien necesite el movimiento —imputar un costo al proyecto,
     * por ejemplo— debe descartar antes esos productos, no confiar en que
     * siempre viene uno de vuelta.
     */
    @Transactional
    public StockMovement applyMovement(Product product, Branch branch, User user, String type,
                                       BigDecimal qty, String refType, String refId, String batch) {
        if (!tracksStock(product)) {
            return null;
        }
        Long companyId = tenant.getCompanyId();
        String lot = batch != null ? batch : "";

        StockMovement m = new StockMovement();
        m.setCompanyId(companyId);
        m.setProduct(product);
        m.setBranch(branch);
        m.setUser(user);
        m.setMovementType(type);
        m.setQuantity(qty);
        m.setRefType(refType);
        m.setRefId(refId);
        movements.save(m);

        ProductStock ps = stock.findByCompanyIdAndProductIdAndBranchIdAndBatch(
                companyId, product.getId(), branch.getId(), lot).orElse(null);
        if (ps == null) {
            ps = new ProductStock();
            ps.setCompanyId(companyId);
            ps.setProduct(product);
            ps.setBranch(branch);
            ps.setBatch(lot);
            ps.setQuantity(BigDecimal.ZERO);
            ps = stock.save(ps);
        }
        ps.setQuantity(ps.getQuantity().add(qty));
        stock.update(ps);
        return m;
    }

    private static StockDtos.StockRow toStockRow(ProductStock s) {
        Product p = s.getProduct();
        Branch b = s.getBranch();
        return new StockDtos.StockRow(s.getId(),
                p != null ? p.getId() : null, p != null ? p.getSku() : null, p != null ? p.getName() : null,
                b != null ? b.getId() : null, b != null ? b.getName() : null,
                s.getQuantity(), s.getBatch(), s.getExpiry());
    }

    private static StockDtos.Movement toMovement(StockMovement m) {
        Product p = m.getProduct();
        Branch b = m.getBranch();
        User u = m.getUser();
        return new StockDtos.Movement(m.getId(),
                p != null ? p.getId() : null, p != null ? p.getName() : null,
                b != null ? b.getId() : null, b != null ? b.getName() : null,
                m.getMovementType(), m.getQuantity(), m.getRefType(), m.getRefId(),
                u != null ? u.getId() : null, m.getCreatedAt());
    }
}
