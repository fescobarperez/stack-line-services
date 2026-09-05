package com.erp_maya.pos.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.fel.dto.FelDtos;
import com.erp_maya.fel.service.FelService;
import com.erp_maya.inventory.service.StockService;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.pos.domain.CashRegister;
import com.erp_maya.pos.domain.Sale;
import com.erp_maya.pos.domain.SaleItem;
import com.erp_maya.pos.dto.SaleDtos;
import com.erp_maya.marketing.domain.PromotionUsage;
import com.erp_maya.marketing.repository.PromotionUsageRepository;
import com.erp_maya.pos.repository.CashRegisterRepository;
import com.erp_maya.pos.repository.SaleRepository;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.UserRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/** Ventas del POS: crea la venta con su detalle, descuenta stock y cuadra la caja. */
@Singleton
public class SaleService {

    // IVA Guatemala 12% incluido en el precio.
    private static final BigDecimal IVA_FACTOR = new BigDecimal("12").divide(new BigDecimal("112"), 10, RoundingMode.HALF_UP);

    private final SaleRepository sales;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final ClientRepository clients;
    private final UserRepository users;
    private final CashRegisterRepository registers;
    private final PromotionUsageRepository promotionUsage;
    private final StockService stockService;
    private final FelService fel;
    private final TenantContext tenant;

    public SaleService(SaleRepository sales, ProductRepository products, BranchRepository branches,
                       ClientRepository clients, UserRepository users, CashRegisterRepository registers,
                       PromotionUsageRepository promotionUsage,
                       StockService stockService, FelService fel, TenantContext tenant) {
        this.sales = sales;
        this.products = products;
        this.branches = branches;
        this.clients = clients;
        this.users = users;
        this.registers = registers;
        this.promotionUsage = promotionUsage;
        this.stockService = stockService;
        this.fel = fel;
        this.tenant = tenant;
    }

    @Transactional
    public Page<SaleDtos.Response> list(Pageable pageable) {
        return sales.findByCompanyIdOrderBySaleDateDesc(tenant.getCompanyId(), pageable)
                .map(SaleService::toResponse);
    }

    @Transactional
    public SaleDtos.Response get(Long id) {
        return toResponse(sales.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Venta " + id + " no encontrada")));
    }

    @Transactional
    public SaleDtos.Response create(SaleDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        Branch branch = branches.findByIdAndCompanyId(req.branchId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal " + req.branchId() + " no encontrada"));
        User user = req.userId() == null ? null
                : users.findByIdAndCompanyId(req.userId(), companyId).orElse(null);

        Sale sale = new Sale();
        sale.setCompanyId(companyId);
        sale.setBranch(branch);
        sale.setUser(user);
        sale.setSaleDate(Instant.now());
        sale.setPaymentMethod(req.paymentMethod());
        sale.setStatus(req.status() != null ? req.status() : "paid");
        sale.setDocNumber(req.docNumber() != null && !req.docNumber().isBlank()
                ? req.docNumber() : "T-" + Instant.now().toEpochMilli());
        if (req.clientId() != null) {
            clients.findByIdAndCompanyId(req.clientId(), companyId).ifPresent(sale::setClient);
        }
        // Toda venta de POS pertenece a un turno abierto del día. Sin esto, las
        // reglas de CashRegisterService se saltan llamando la API directamente y
        // las ventas de hoy se acumularían en el arqueo de un turno viejo.
        CashRegister register = registers.findByIdAndCompanyId(req.cashRegisterId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Turno de caja " + req.cashRegisterId() + " no encontrado"));
        if (!"open".equals(register.getStatus())) {
            throw new IllegalStateException("El turno de caja está " + register.getStatus()
                    + "; abre una caja antes de cobrar.");
        }
        if (register.getBusinessDate() != null && !LocalDate.now().equals(register.getBusinessDate())) {
            throw new IllegalStateException("El turno abierto es del " + register.getBusinessDate()
                    + ". Ciérralo con el arqueo de ese día antes de cobrar hoy.");
        }
        sale.setCashRegister(register);

        BigDecimal total = BigDecimal.ZERO;
        for (SaleDtos.ItemRequest ir : req.items()) {
            Product product = products.findByIdAndCompanyId(ir.productId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto " + ir.productId() + " no encontrado"));
            BigDecimal discount = ir.discount() != null ? ir.discount() : BigDecimal.ZERO;
            BigDecimal lineTotal = ir.unitPrice().multiply(ir.quantity()).subtract(discount);

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(ir.quantity());
            item.setUnitPrice(ir.unitPrice());
            item.setDiscount(discount);
            // El origen decide si el descuento necesitó autorización y alimenta
            // las estadísticas de promociones.
            item.setDiscountSource(discount.signum() == 0 ? "none"
                    : (ir.promotionId() != null ? "promo"
                       : (ir.discountSource() != null ? ir.discountSource() : "manual")));
            item.setPromotionId(ir.promotionId());
            item.setLineTotal(lineTotal);
            sale.addItem(item);
            total = total.add(lineTotal);
        }

        // Descuento manual sobre el total, aparte de los de línea.
        BigDecimal headerDiscount = req.discountTotal() != null ? req.discountTotal() : BigDecimal.ZERO;
        if (headerDiscount.signum() > 0) {
            if (headerDiscount.compareTo(total) > 0) {
                throw new IllegalStateException("El descuento no puede superar el total de la venta");
            }
            BigDecimal base = total;
            total = total.subtract(headerDiscount);
            sale.setDiscountTotal(headerDiscount);
            // El % efectivo es lo que evalúa la regla de autorización: Q500 sobre
            // una compra de Q600 es 83%, aunque el monto parezca pequeño.
            sale.setDiscountPercent(base.signum() == 0 ? BigDecimal.ZERO
                    : headerDiscount.multiply(BigDecimal.valueOf(100))
                        .divide(base, 3, RoundingMode.HALF_UP));
        }

        BigDecimal tax = total.multiply(IVA_FACTOR).setScale(2, RoundingMode.HALF_UP);
        sale.setTotal(total);
        sale.setTax(tax);
        sale.setSubtotal(total.subtract(tax));
        Sale saved = sales.save(sale);

        // Uso de promociones: hasta ahora esta tabla no se alimentaba de ventas
        // reales, así que los "usos" y "ahorro" del módulo salían de datos mock.
        for (SaleItem item : saved.getItems()) {
            if (item.getPromotionId() == null || item.getDiscount().signum() == 0) continue;
            PromotionUsage use = new PromotionUsage();
            use.setCompanyId(companyId);
            use.setPromotionId(item.getPromotionId());
            use.setSaleId(saved.getId());
            use.setAmountSaved(item.getDiscount());
            use.setReference(saved.getDocNumber());
            promotionUsage.save(use);
        }

        // Kardex + descuento de existencias por cada renglón.
        for (SaleItem item : saved.getItems()) {
            stockService.applyMovement(item.getProduct(), branch, user, "sale",
                    item.getQuantity().negate(), "sale", saved.getDocNumber(), "");
        }

        // Cuadre de caja.
        if (register != null) {
            register.setSalesTotal(register.getSalesTotal().add(total));
            if ("efectivo".equalsIgnoreCase(req.paymentMethod())) {
                register.setSalesCash(register.getSalesCash().add(total));
            } else if (req.paymentMethod() != null && req.paymentMethod().toLowerCase().contains("tarjeta")) {
                register.setSalesCard(register.getSalesCard().add(total));
            }
            registers.update(register);
        }

        // DTE de la venta. En Guatemala toda venta pagada emite factura, así que se
        // certifica en la misma transacción: si el DTE falla, la venta no queda huérfana.
        // Nota: FelService.certify() hoy simula al certificador; cuando se integre el
        // proveedor real habrá que sacar esto de la transacción y manejar contingencia.
        if ("paid".equalsIgnoreCase(saved.getStatus())) {
            fel.certify(new FelDtos.CertifyRequest(saved.getId(), "FACT", "A"));
        }

        return toResponse(saved);
    }

    private static SaleDtos.Response toResponse(Sale s) {
        var items = s.getItems().stream().map(i -> new SaleDtos.ItemResponse(
                i.getId(), i.getProduct() != null ? i.getProduct().getId() : null,
                i.getProduct() != null ? i.getProduct().getName() : null,
                i.getQuantity(), i.getUnitPrice(), i.getDiscount(), i.getLineTotal())).toList();
        return new SaleDtos.Response(s.getId(), s.getDocNumber(),
                s.getClient() != null ? s.getClient().getId() : null,
                s.getClient() != null ? s.getClient().getName() : null,
                s.getBranch() != null ? s.getBranch().getId() : null,
                s.getBranch() != null ? s.getBranch().getName() : null,
                s.getUser() != null ? s.getUser().getId() : null,
                s.getCashRegister() != null ? s.getCashRegister().getId() : null,
                s.getSaleDate(), s.getPaymentMethod(), s.getSubtotal(), s.getTax(), s.getTotal(),
                s.getStatus(), items);
    }
}
