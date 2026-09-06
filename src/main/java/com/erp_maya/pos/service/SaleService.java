package com.erp_maya.pos.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.authorization.dto.AuthorizationDtos;
import com.erp_maya.authorization.service.AuthorizationService;
import com.erp_maya.project.domain.Project;
import com.erp_maya.project.repository.ProjectRepositories;
import com.erp_maya.sequence.service.DocumentSequenceService;
import com.erp_maya.settings.service.TaxService;
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
import java.util.Set;

/** Ventas del POS: crea la venta con su detalle, descuenta stock y cuadra la caja. */
@Singleton
public class SaleService {

    /**
     * Catálogo de DTE de SAT que el sistema sabe emitir. La columna no lleva
     * CHECK a propósito (ver la 045): el catálogo crece y una restricción de
     * base obligaría a migrar por cada tipo. La validación vive aquí, donde
     * agregar un tipo es una línea.
     */
    private static final Set<String> DOC_TYPES = Set.of(
            "FACT", "FCAM", "FPEQ", "FESP", "NCRE", "NDEB", "NABN", "RECI");

    /** Los que modifican otro documento y por tanto exigen referencia. */
    private static final Set<String> AMENDING_TYPES = Set.of("NCRE", "NDEB", "NABN");

    /** Los que restan. Debe coincidir con el CASE de signed_total en la 045. */
    private static final Set<String> NEGATIVE_TYPES = Set.of("NCRE", "NABN");

    // IVA Guatemala 12% incluido en el precio.

    private final SaleRepository sales;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final ClientRepository clients;
    private final UserRepository users;
    private final CashRegisterRepository registers;
    private final PromotionUsageRepository promotionUsage;
    private final StockService stockService;
    private final FelService fel;
    private final TaxService taxService;
    private final ProjectRepositories.Projects projects;
    private final DocumentSequenceService sequences;
    private final AuthorizationService authorizations;
    private final TenantContext tenant;

    public SaleService(SaleRepository sales, ProductRepository products, BranchRepository branches,
                       ClientRepository clients, UserRepository users, CashRegisterRepository registers,
                       PromotionUsageRepository promotionUsage,
                       StockService stockService, FelService fel, TenantContext tenant,
                       TaxService taxService, ProjectRepositories.Projects projects,
                       AuthorizationService authorizations, DocumentSequenceService sequences) {
        this.projects = projects;
        this.sequences = sequences;
        this.authorizations = authorizations;
        this.sales = sales;
        this.products = products;
        this.branches = branches;
        this.clients = clients;
        this.users = users;
        this.registers = registers;
        this.promotionUsage = promotionUsage;
        this.stockService = stockService;
        this.fel = fel;
        this.taxService = taxService;
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
        User user = req.userId() == null ? null
                : users.findByIdAndCompanyId(req.userId(), companyId).orElse(null);
        // La sucursal es obligatoria en la base, pero quien factura un avance de
        // proyecto desde la oficina no tiene por qué elegirla: se toma la del
        // usuario y, si no tiene, la de la empresa. El POS la sigue mandando.
        Branch branch = resolveBranch(req.branchId(), user, companyId);

        Sale sale = new Sale();
        sale.setCompanyId(companyId);
        sale.setBranch(branch);
        sale.setUser(user);
        sale.setSaleDate(Instant.now());
        sale.setPaymentMethod(req.paymentMethod());
        sale.setStatus(req.status() != null ? req.status() : "paid");
        // Tipo de documento. Sin `docType` es factura, que es lo que el POS
        // emitía implícitamente antes de la 045: nada de lo existente cambia.
        String docType = req.docType() == null || req.docType().isBlank()
                ? "FACT" : req.docType().toUpperCase();
        if (!DOC_TYPES.contains(docType)) {
            throw new IllegalArgumentException("Tipo de documento no reconocido: " + docType);
        }
        // Una NC/ND que no dice a qué documento modifica es inservible: SAT la
        // rechaza y sin la referencia tampoco se puede cuadrar contra el original.
        if (AMENDING_TYPES.contains(docType) && req.relatedSaleId() == null) {
            throw new IllegalStateException(
                    "Una " + docType + " debe referenciar el documento que modifica");
        }
        if (req.relatedSaleId() != null) {
            sales.findByIdAndCompanyId(req.relatedSaleId(), companyId).orElseThrow(
                    () -> new ResourceNotFoundException(
                            "Documento " + req.relatedSaleId() + " no encontrado"));
            sale.setRelatedSaleId(req.relatedSaleId());
        }
        sale.setDocType(docType);
        String series = req.series() != null && !req.series().isBlank() ? req.series() : "A";
        sale.setSeries(series);
        sale.setReason(req.reason());
        // Correlativo por tipo y serie. Antes esto era "T-" + epochMillis: ni
        // correlativo, ni legible, ni buscable. Se respeta el número que venga
        // en la petición por si se está migrando historia de otro sistema.
        sale.setDocNumber(req.docNumber() != null && !req.docNumber().isBlank()
                ? req.docNumber() : sequences.next(docType, series));
        if (req.clientId() != null) {
            clients.findByIdAndCompanyId(req.clientId(), companyId).ifPresent(sale::setClient);
        }
        boolean credit = Boolean.TRUE.equals(req.credit());
        sale.setCredit(credit);

        // Una venta al fiado no mueve dinero, así que no pertenece a ningún
        // arqueo: facturar un avance de proyecto a crédito es trabajo de
        // oficina y no debería exigir que haya una caja abierta.
        //
        // Lo que NO se permite es una venta de CONTADO sin turno: sería la
        // puerta de atrás al control de cajas que valida justo aquí abajo.
        CashRegister register = null;
        if (req.cashRegisterId() != null) {
            register = registers.findByIdAndCompanyId(req.cashRegisterId(), companyId)
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
        } else if (!credit) {
            throw new IllegalStateException(
                    "Una venta de contado necesita un turno de caja abierto.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (SaleDtos.ItemRequest ir : req.items()) {
            // Sin producto es un renglón de servicio: no existe en catálogo, no
            // toca bodega y su nombre es el concepto que venga en la petición.
            if (ir.productId() == null && (ir.concept() == null || ir.concept().isBlank())) {
                throw new IllegalStateException(
                        "Un renglón sin producto necesita un concepto que describa lo que se cobra");
            }
            Product product = ir.productId() == null ? null
                    : products.findByIdAndCompanyId(ir.productId(), companyId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Producto " + ir.productId() + " no encontrado"));
            BigDecimal discount = ir.discount() != null ? ir.discount() : BigDecimal.ZERO;
            BigDecimal lineTotal = ir.unitPrice().multiply(ir.quantity()).subtract(discount);

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setConcept(ir.concept());
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
        sale.setAuthorizationId(req.authorizationId());
        sale.setProjectId(req.projectId());
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

        BigDecimal rate = taxService.rate();
        BigDecimal tax = total.multiply(TaxService.factorOverGross(rate)).setScale(2, RoundingMode.HALF_UP);
        sale.setTaxRate(rate);
        sale.setTotal(total);
        // Después de calcular el total y antes de guardar: el umbral se mide
        // sobre el monto del documento, que hasta esta línea no existía.
        assertBillable(req.projectId(), sale, req.authorizationId());
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
            if (item.getProduct() == null) continue;   // renglón de servicio
            stockService.applyMovement(item.getProduct(), branch, user, "sale",
                    item.getQuantity().negate(), "sale", saved.getDocNumber(), "");
        }

        // Cuadre de caja. El crédito queda fuera: al cerrar el turno se cuenta
        // el dinero que hay en la gaveta, y de una venta al fiado no entró
        // ninguno. Cuando se cobre, será un abono con su propio recibo.
        if (register != null && !credit) {
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
            fel.certify(new FelDtos.CertifyRequest(
                    saved.getId(), saved.getDocType(), saved.getSeries()));
        }

        return toResponse(saved);
    }

    private Branch resolveBranch(Long branchId, User user, Long companyId) {
        if (branchId != null) {
            return branches.findByIdAndCompanyId(branchId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sucursal " + branchId + " no encontrada"));
        }
        if (user != null && user.getBranch() != null) return user.getBranch();
        return branches.findByCompanyId(companyId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "La empresa no tiene ninguna sucursal configurada"));
    }

    /**
     * Facturar por encima de lo contratado necesita autorización.
     *
     * No es un bloqueo: facturar de más pasa legítimamente —una orden de cambio
     * que todavía no se refleja en el contratado—. Pero no debe pasar en
     * silencio, porque lo contratado es la referencia contra la que se mide el
     * margen del proyecto entero.
     *
     * Se evalúa contra lo YA facturado más este documento, que es lo que
     * quedaría si se emite. Mismo camino que ProjectService.assertAuthorized:
     * el consumidor nunca decide si hace falta, se lo pregunta al motor.
     */
    private void assertBillable(Long projectId, Sale sale, Long authorizationId) {
        if (projectId == null) return;
        Project p = projects.findById(projectId).orElse(null);
        if (p == null || p.getContractedAmount() == null
                || p.getContractedAmount().signum() <= 0) return;

        BigDecimal already = sales.findByCompanyIdAndProjectId(p.getCompanyId(), p.getId()).stream()
                .filter(x -> !"cancelled".equalsIgnoreCase(String.valueOf(x.getStatus())))
                .map(x -> x.getSignedTotal() != null ? x.getSignedTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal after = already.add(sale.getTotal());
        BigDecimal pct = after.multiply(new BigDecimal("100"))
                .divide(p.getContractedAmount(), 3, RoundingMode.HALF_UP);

        var check = authorizations.evaluate(new AuthorizationDtos.EvaluateRequest(
                "project_overbill", null, sale.getTotal(), p.getCurrency(), pct));
        if (!check.required()) return;

        if (authorizationId == null) {
            throw new IllegalStateException(String.format(
                    "Este documento llevaría lo facturado al %.1f%% de lo contratado del proyecto %s "
                    + "y requiere autorización de %s.", pct, p.getCode(), check.levelName()));
        }
        var auth = authorizations.get(authorizationId);
        if (!"approved".equals(auth.status()) || !"project_overbill".equals(auth.type())) {
            throw new IllegalStateException(
                    "La autorización " + authorizationId + " no es válida para facturar este proyecto");
        }
    }

    /**
     * El signo para la respuesta. La columna generada es la fuente de verdad y
     * la que suman todas las agregaciones; pero recién insertada la entidad
     * todavía no la trae de vuelta, y devolver null en el POST sería un bache
     * para el consumidor. Mismo criterio que la 045, aplicado en memoria.
     */
    private static BigDecimal signedOf(Sale s) {
        if (s.getSignedTotal() != null) return s.getSignedTotal();
        BigDecimal t = s.getTotal() != null ? s.getTotal() : BigDecimal.ZERO;
        return NEGATIVE_TYPES.contains(s.getDocType()) ? t.negate() : t;
    }

    private static SaleDtos.Response toResponse(Sale s) {
        var items = s.getItems().stream().map(i -> new SaleDtos.ItemResponse(
                i.getId(), i.getProduct() != null ? i.getProduct().getId() : null,
                i.getProduct() != null ? i.getProduct().getName() : i.getConcept(),
                i.getQuantity(), i.getUnitPrice(), i.getDiscount(), i.getLineTotal())).toList();
        return new SaleDtos.Response(s.getId(), s.getDocNumber(), s.getDocType(), s.getSeries(),
                s.getRelatedSaleId(), s.getReason(),
                s.getClient() != null ? s.getClient().getId() : null,
                s.getClient() != null ? s.getClient().getName() : null,
                s.getBranch() != null ? s.getBranch().getId() : null,
                s.getBranch() != null ? s.getBranch().getName() : null,
                s.getUser() != null ? s.getUser().getId() : null,
                s.getCashRegister() != null ? s.getCashRegister().getId() : null,
                s.getSaleDate(), s.getPaymentMethod(), s.getSubtotal(), s.getTax(), s.getTaxRate(), s.getTotal(), signedOf(s), s.isCredit(), s.getProjectId(),
                s.getStatus(), items);
    }
}
