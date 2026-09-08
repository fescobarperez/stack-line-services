package com.erp_maya.project.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.accounting.service.PostingService;
import com.erp_maya.authorization.dto.AuthorizationDtos;
import com.erp_maya.authorization.service.AuthorizationService;
import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.inventory.domain.ProductStock;
import com.erp_maya.inventory.repository.ProductStockRepository;
import com.erp_maya.inventory.service.StockService;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.UserRepository;
import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.project.domain.Project;
import com.erp_maya.project.domain.ProjectCost;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.dto.ProjectDtos;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.project.repository.ProjectQuoteRepository;
import com.erp_maya.project.repository.ProjectRepositories.Costs;
import com.erp_maya.project.repository.ProjectRepositories.Projects;
import com.erp_maya.payable.repository.PurchaseInvoiceRepository;
import com.erp_maya.pos.repository.SaleRepository;
import com.erp_maya.purchasing.repository.PurchaseOrderRepository;
import com.erp_maya.receivable.domain.Payment;
import com.erp_maya.receivable.repository.PaymentRepository;
import com.erp_maya.quote.repository.QuoteChargeRepository;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.quote.dto.QuoteChargeDtos;
import com.erp_maya.quote.service.QuoteChargeService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Proyectos y su rentabilidad.
 *
 * El seguimiento son cuatro cifras, no una:
 *   contratado    lo que se vendió, congelado al convertir la cotización
 *   ejecutado     costo real ya incurrido (compras, materia prima, mano de obra)
 *   comprometido  pedido y aún no facturado — lo que ya te vas a gastar
 *   cobrado       adelantos y pagos recibidos
 *
 * El margen proyectado descuenta también lo comprometido: con una sola cifra
 * de gasto el sobrecosto se ve cuando ya ocurrió.
 */
@Singleton
public class ProjectService {

    private final Projects projects;
    private final Costs costs;
    private final Materials projectMaterials;
    private final QuoteRepository quotes;
    private final ProjectQuoteRepository projectQuotes;
    private final QuoteChargeRepository quoteChargeRepository;
    private final QuoteChargeService quoteCharges;
    private final ClientRepository clients;
    private final PaymentRepository payments;
    private final PurchaseOrderRepository purchaseOrders;
    private final PurchaseInvoiceRepository purchaseInvoices;
    private final SaleRepository sales;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final ProductStockRepository stock;
    private final StockService stockService;
    private final UserRepository users;
    private final AuthorizationService authorizations;
    private final PostingService posting;
    private final TenantContext tenant;

    public ProjectService(Projects projects, Costs costs, Materials projectMaterials, QuoteRepository quotes,
                          ProjectQuoteRepository projectQuotes,
                          QuoteChargeRepository quoteChargeRepository,
                          QuoteChargeService quoteCharges,
                          ClientRepository clients, PaymentRepository payments,
                          PurchaseOrderRepository purchaseOrders,
                          PurchaseInvoiceRepository purchaseInvoices, SaleRepository sales,
                          ProductRepository products, BranchRepository branches,
                          ProductStockRepository stock, StockService stockService,
                          UserRepository users, AuthorizationService authorizations,
                          PostingService posting,
                          TenantContext tenant) {
        this.projects = projects;
        this.costs = costs;
        this.projectMaterials = projectMaterials;
        this.quotes = quotes;
        this.projectQuotes = projectQuotes;
        this.quoteChargeRepository = quoteChargeRepository;
        this.quoteCharges = quoteCharges;
        this.clients = clients;
        this.payments = payments;
        this.purchaseOrders = purchaseOrders;
        this.purchaseInvoices = purchaseInvoices;
        this.sales = sales;
        this.products = products;
        this.branches = branches;
        this.stock = stock;
        this.stockService = stockService;
        this.users = users;
        this.authorizations = authorizations;
        this.posting = posting;
        this.tenant = tenant;
    }

    @Transactional
    public List<ProjectDtos.Response> list() {
        return projects.findByCompanyIdOrderByIdDesc(tenant.getCompanyId())
                .stream().map(p -> toResponse(p, false)).toList();
    }

    @Transactional
    public ProjectDtos.Response get(Long id) {
        return toResponse(find(id), true);
    }

    @Transactional
    public ProjectDtos.Response create(ProjectDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        Project p = new Project();
        p.setCompanyId(companyId);
        p.setClientId(requireClient(req.clientId()).getId());
        p.setCode(req.code() != null && !req.code().isBlank() ? req.code().trim() : nextCode(companyId));
        p.setName(req.name().trim());
        p.setCostCenterId(req.costCenterId());
        p.setContractedAmount(req.contractedAmount() != null ? req.contractedAmount() : BigDecimal.ZERO);
        if (req.currency() != null && !req.currency().isBlank()) p.setCurrency(req.currency());
        p.setStartDate(req.startDate() != null ? req.startDate() : LocalDate.now());
        p.setEndDate(req.endDate());
        p.setNotes(req.notes());
        // Nace en borrador: un proyecto creado a mano no tiene el respaldo
        // comercial de una cotización aprobada, y hasta ahora podía empezar a
        // consumir bodega y acumular costos sin que nadie lo autorizara.
        p.setStatus("draft");
        return toResponse(projects.save(p), true);
    }

    @Transactional
    public ProjectDtos.Response addCost(Long projectId, ProjectDtos.CostRequest req, Long userId) {
        Project p = find(projectId);
        if (!"open".equals(p.getStatus())) {
            throw new IllegalStateException("El proyecto está " + p.getStatus() + "; no admite cargos");
        }
        if (!List.of("purchase", "material", "labor", "other").contains(req.source())) {
            throw new IllegalStateException("Origen de costo no válido: " + req.source());
        }
        assertAuthorized(p, req.amount(), req.authorizationId());
        ProjectCost c = new ProjectCost();
        c.setCompanyId(p.getCompanyId());
        c.setProjectId(p.getId());
        c.setSource(req.source());
        c.setRefId(req.refId());
        c.setDescription(req.description());
        c.setAmount(req.amount());
        c.setCostDate(req.costDate() != null ? req.costDate() : LocalDate.now());
        c.setCreatedBy(userId);
        c.setAuthorizationId(req.authorizationId());
        costs.save(c);
        return toResponse(p, true);
    }

    /**
     * Consume materia prima de bodega y lo carga al proyecto.
     *
     * El costo se toma del promedio del artículo (o de su costo estándar si no
     * hay promedio calculado) en el momento del consumo, no de la compra: si se
     * imputara la compra, el primer proyecto cargaría con el lote entero y los
     * siguientes consumirían gratis.
     */
    @Transactional
    public ProjectDtos.Response consumeMaterial(Long projectId, ProjectDtos.ConsumeRequest req, Long userId) {
        Long companyId = tenant.getCompanyId();
        Project p = find(projectId);
        if (!"open".equals(p.getStatus())) {
            throw new IllegalStateException("El proyecto está " + p.getStatus() + "; no admite consumos");
        }
        if (req.quantity() == null || req.quantity().signum() <= 0) {
            throw new IllegalStateException("La cantidad debe ser mayor a cero");
        }
        Product product = products.findByIdAndCompanyId(req.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Artículo " + req.productId() + " no encontrado"));
        if ("service".equals(product.getItemType()) || Boolean.FALSE.equals(product.getTracksStock())) {
            throw new IllegalStateException(
                    "Un servicio no se consume de bodega; regístralo como mano de obra");
        }
        Branch branch = branches.findByIdAndCompanyId(req.branchId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal " + req.branchId() + " no encontrada"));

        // applyMovement no valida existencias: sin esto el stock quedaría negativo.
        String lot = req.batch() != null ? req.batch() : "";
        BigDecimal onHand = stock
                .findByCompanyIdAndProductIdAndBranchIdAndBatch(companyId, product.getId(), branch.getId(), lot)
                .map(ProductStock::getQuantity).orElse(BigDecimal.ZERO);
        if (onHand.compareTo(req.quantity()) < 0) {
            throw new IllegalStateException("Existencias insuficientes de " + product.getName()
                    + ": hay " + onHand + " y se piden " + req.quantity());
        }

        BigDecimal unitCost = product.getAvgCost() != null && product.getAvgCost().signum() > 0
                ? product.getAvgCost()
                : (product.getCost() != null ? product.getCost() : BigDecimal.ZERO);
        BigDecimal amount = unitCost.multiply(req.quantity()).setScale(2, RoundingMode.HALF_UP);
        assertAuthorized(p, amount, req.authorizationId());

        User user = userId == null ? null : users.findByIdAndCompanyId(userId, companyId).orElse(null);
        var movement = stockService.applyMovement(product, branch, user, "project_consumption",
                req.quantity().negate(), "project", p.getCode(), req.batch());

        // El material que sale de bodega deja de ser inventario y pasa a ser
        // costo del proyecto. Sin este asiento, el balance seguiría mostrando
        // como existencias algo que ya se gastó.
        //
        //   Costo de ventas   débito   al costo promedio
        //   Inventario                 crédito
        //
        // Va al costo del proyecto vía su centro de costo, que es lo que
        // permite filtrar el gasto por obra en los reportes contables.
        posting.post("project_consumption", movement.getId(), LocalDate.now(),
                "Consumo " + product.getName() + " · " + p.getCode(), p.getCode(),
                PostingService.lines(
                        PostingService.Line.debit("posting.cost_of_sales", amount,
                                product.getName() + " × " + req.quantity(), p.getCostCenterId()),
                        PostingService.Line.credit("posting.inventory", amount,
                                product.getName(), p.getCostCenterId())));

        ProjectCost c = new ProjectCost();
        c.setCompanyId(companyId);
        c.setProjectId(p.getId());
        c.setSource("material");
        c.setRefId(movement.getId());
        c.setDescription(product.getName() + " × " + req.quantity()
                + (req.notes() != null && !req.notes().isBlank() ? " · " + req.notes() : ""));
        c.setAmount(amount);
        c.setCostDate(LocalDate.now());
        c.setCreatedBy(userId);
        c.setAuthorizationId(req.authorizationId());
        costs.save(c);
        return toResponse(p, true);
    }

    @Transactional
    public void deleteCost(Long projectId, Long costId) {
        ProjectCost c = costs.findByIdAndCompanyId(costId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cargo " + costId + " no encontrado"));
        if (!c.getProjectId().equals(projectId)) {
            throw new IllegalStateException("El cargo no pertenece a este proyecto");
        }
        costs.delete(c);
    }

    /**
     * Cambia el estado. `note` solo se usa al cerrar, y solo hace falta cuando
     * queda costo sin facturar.
     */
    @Transactional
    public ProjectDtos.Response setStatus(Long id, String status, String note) {
        if (!List.of("draft", "open", "closed", "cancelled").contains(status)) {
            throw new IllegalStateException("Estado no válido: " + status);
        }
        Project p = find(id);

        if ("closed".equals(status)) {
            // Aquí es donde de verdad se obliga a facturar todo: no al abrir el
            // proyecto —eso le cobraría IVA al cliente por trabajo no hecho—
            // sino al liquidarlo. Cerrar con costo sin facturar es legítimo
            // (una garantía absorbida, un descuento pactado al final), pero es
            // plata gastada que no se le cobró a nadie: se exige decir por qué.
            BigDecimal executed = totalExecuted(p);
            BigDecimal invoiced = totalInvoiced(p);
            if (invoiced.compareTo(executed) < 0
                    && (note == null || note.isBlank())) {
                throw new IllegalStateException(
                        "El proyecto tiene " + executed.subtract(invoiced) + " " + p.getCurrency()
                        + " de costo sin facturar (ejecutado " + executed + ", facturado " + invoiced
                        + "). Factura la diferencia o justifica el cierre.");
            }
            if (note != null && !note.isBlank()) p.setCloseNote(note.trim());
            if (p.getEndDate() == null) p.setEndDate(LocalDate.now());
        }

        p.setStatus(status);
        return toResponse(projects.update(p), true);
    }

    // ── Interno ──────────────────────────────────────────────────────────
    /**
     * Un cargo que lleve el proyecto sobre el umbral necesita autorización.
     *
     * El porcentaje evaluado incluye el cargo nuevo: es lo que consumiría del
     * contratado si se registra. Se consulta al motor —el consumidor nunca
     * decide si hace falta— y se exige una autorización aprobada de ese tipo.
     */
    private void assertAuthorized(Project p, BigDecimal newAmount, Long authorizationId) {
        BigDecimal contracted = p.getContractedAmount();
        if (contracted == null || contracted.signum() <= 0) return;   // sin contratado no hay umbral

        BigDecimal already = costs.findByProjectIdOrderByCostDateDesc(p.getId())
                .stream().map(ProjectCost::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pct = already.add(newAmount).multiply(new BigDecimal("100"))
                .divide(contracted, 3, RoundingMode.HALF_UP);

        var check = authorizations.evaluate(new AuthorizationDtos.EvaluateRequest(
                "project_overrun", null, newAmount, p.getCurrency(), pct));
        if (!check.required()) return;

        if (authorizationId == null) {
            throw new IllegalStateException(String.format(
                    "Este cargo llevaría el proyecto al %.1f%% de lo contratado y requiere autorización de %s.",
                    pct, check.levelName()));
        }
        var auth = authorizations.get(authorizationId);
        if (!"approved".equals(auth.status()) || !"project_overrun".equals(auth.type())) {
            throw new IllegalStateException("La autorización " + authorizationId + " no es válida para este cargo");
        }
    }

    private Client requireClient(Long clientId) {
        return clients.findByIdAndCompanyId(clientId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente " + clientId + " no encontrado"));
    }

    private String nextCode(Long companyId) {
        return String.format("PRY-%04d", projects.countByCompanyId(companyId) + 1);
    }

    private Project find(Long id) {
        return projects.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto " + id + " no encontrado"));
    }

    /** Costo incurrido: la suma de los cargos del proyecto. */
    private BigDecimal totalExecuted(Project p) {
        return costs.findByProjectIdOrderByCostDateDesc(p.getId()).stream()
                .map(ProjectCost::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Facturado al cliente. Suma `signedTotal` y no `total` (ver la 045): una
     * nota de crédito contra el proyecto resta sola, sin que nadie tenga que
     * acordarse del signo aquí.
     */
    private BigDecimal totalInvoiced(Project p) {
        return sales.findByCompanyIdAndProjectId(p.getCompanyId(), p.getId()).stream()
                .filter(x -> !"cancelled".equalsIgnoreCase(String.valueOf(x.getStatus())))
                .map(x -> x.getSignedTotal() != null ? x.getSignedTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ProjectDtos.Response toResponse(Project p, boolean withCosts) {
        List<ProjectMaterial> materialRows = projectMaterials
                .findByCompanyIdAndProjectIdOrderByIdAsc(p.getCompanyId(), p.getId());
        BigDecimal materialsCost = materialRows.stream()
                .map(this::plannedMaterialAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Separación por estado de la cotización del material (regla 6):
        //   aprobado  → materiales cuya cotización está aprobada: costo firme.
        //   tentativo → materiales en cotización no aprobada (borrador/enviada):
        //               base del margen estimado, aún no firme.
        //   sin cotización (quote_id == NULL) → no cuenta en ninguno: aún no es
        //               parte de ninguna oferta comercial.
        BigDecimal materialsCostApproved = BigDecimal.ZERO;
        BigDecimal materialsCostTentative = BigDecimal.ZERO;
        java.util.Map<Long, Boolean> quoteApproved = new java.util.HashMap<>();
        for (ProjectMaterial m : materialRows) {
            if (m.getQuoteId() == null) continue;
            boolean approved = quoteApproved.computeIfAbsent(m.getQuoteId(), qid ->
                    quotes.findByIdAndCompanyId(qid, p.getCompanyId())
                            .map(q -> approvedQuoteStatus(q.getStatus()))
                            .orElse(Boolean.FALSE));
            BigDecimal amt = plannedMaterialAmount(m);
            if (approved) materialsCostApproved = materialsCostApproved.add(amt);
            else materialsCostTentative = materialsCostTentative.add(amt);
        }
        // Gastos del proyecto = DERIVADOS de sus cotizaciones (modelo agregado):
        //   materiales en cotización (materialsCostApproved + tentative) +
        //   los cargos manuales (quote_charges) de esas cotizaciones.
        // Ya no se usa project_cost como fuente de gastos.
        BigDecimal materialsInQuotes = materialsCostApproved.add(materialsCostTentative);
        BigDecimal quoteChargesTotal = projectQuotes
                .findByCompanyIdAndProjectId(p.getCompanyId(), p.getId()).stream()
                .flatMap(link -> quoteChargeRepository
                        .findByCompanyIdAndQuoteIdOrderBySortOrderAsc(p.getCompanyId(), link.getQuoteId())
                        .stream())
                .map(c -> c.getComputedAmount() == null ? BigDecimal.ZERO : c.getComputedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal executed = materialsInQuotes.add(quoteChargesTotal);
        // Comprometido: órdenes del proyecto que todavía no tienen factura.
        // No se suma al ejecutado —una OC puede cancelarse— pero descontarlo del
        // margen proyectado es lo que permite ver venir un sobrecosto.
        List<Long> invoicedPOs = purchaseInvoices
                .findByCompanyIdAndProjectId(p.getCompanyId(), p.getId())
                .stream().map(i -> i.getPurchaseOrder() != null ? i.getPurchaseOrder().getId() : null)
                .filter(java.util.Objects::nonNull).toList();
        BigDecimal committed = purchaseOrders
                .findByCompanyIdAndProjectId(p.getCompanyId(), p.getId())
                .stream()
                .filter(po -> !"cancelled".equalsIgnoreCase(String.valueOf(po.getStatus())))
                .filter(po -> !invoicedPOs.contains(po.getId()))
                .map(po -> po.getTotal() != null ? po.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Facturado y cobrado son distintos: se factura a crédito sin cobrar, y
        // se cobra un anticipo sin haber facturado.
        BigDecimal invoiced = totalInvoiced(p);

        List<Payment> paid = payments
                .findByCompanyIdAndProjectIdOrderByPaymentDateDesc(p.getCompanyId(), p.getId());
        BigDecimal collected = paid.stream().map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Contratado = Σ del total de las cotizaciones APROBADAS/incluidas del
        // proyecto (la fuente de verdad es la cotización, no un campo estático).
        // Un borrador no es contratado todavía. Cae al campo del proyecto solo
        // si no hay ninguna cotización aprobada (compatibilidad / proyectos viejos).
        BigDecimal quotesContracted = projectQuotes
                .findByCompanyIdAndProjectId(p.getCompanyId(), p.getId()).stream()
                .map(link -> quotes.findByIdAndCompanyId(link.getQuoteId(), p.getCompanyId()).orElse(null))
                .filter(qq -> qq != null && approvedQuoteStatus(qq.getStatus()))
                .map(qq -> quoteCharges.getSummary(qq.getId()).total())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal contracted = quotesContracted.signum() > 0
                ? quotesContracted
                : (p.getContractedAmount() != null ? p.getContractedAmount() : BigDecimal.ZERO);
        BigDecimal margin = contracted.subtract(executed);
        BigDecimal projected = margin.subtract(committed);
        BigDecimal marginPct = contracted.signum() == 0 ? BigDecimal.ZERO
                : margin.multiply(new BigDecimal("100")).divide(contracted, 2, RoundingMode.HALF_UP);

        String clientName = clients.findByIdAndCompanyId(p.getClientId(), p.getCompanyId())
                .map(Client::getName).orElse(null);
        String quoteNumber = p.getQuoteId() == null ? null
                : quotes.findByIdAndCompanyId(p.getQuoteId(), p.getCompanyId())
                    .map(Quote::getDocNumber).orElse(null);

        // Cotizaciones asociadas al proyecto, para la vista de agregación.
        List<com.erp_maya.project.domain.ProjectQuote> quoteLinks =
                projectQuotes.findByCompanyIdAndProjectId(p.getCompanyId(), p.getId());
        int quoteCount = quoteLinks.size();
        List<ProjectDtos.QuoteRef> quoteRefs = withCosts
                ? quoteLinks.stream()
                    .map(link -> {
                        Quote q = quotes.findByIdAndCompanyId(link.getQuoteId(), p.getCompanyId()).orElse(null);
                        // Materiales de esta cotización (derivado).
                        BigDecimal qMaterials = materialRows.stream()
                                .filter(m -> link.getQuoteId().equals(m.getQuoteId()))
                                .map(this::plannedMaterialAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        // Cargos manuales de esta cotización.
                        List<ProjectDtos.QuoteChargeRef> qCharges = quoteChargeRepository
                                .findByCompanyIdAndQuoteIdOrderBySortOrderAsc(p.getCompanyId(), link.getQuoteId())
                                .stream()
                                .map(c -> new ProjectDtos.QuoteChargeRef(c.getCategory(), c.getDescription(),
                                        c.getCalcType(), money(c.getComputedAmount())))
                                .toList();
                        BigDecimal chargesSum = qCharges.stream()
                                .map(ProjectDtos.QuoteChargeRef::computedAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        QuoteChargeDtos.Summary pricing = quoteCharges.getSummary(link.getQuoteId());
                        return new ProjectDtos.QuoteRef(
                                link.getQuoteId(),
                                q != null ? q.getDocNumber() : ("COT-" + link.getQuoteId()),
                                q != null ? q.getStatus() : "desconocido",
                                money(pricing.operatingCost()), money(pricing.profitAmount()),
                                money(pricing.taxableSubtotal()), money(pricing.taxRate()), money(pricing.tax()), money(pricing.total()),
                                link.getIncluded(), money(qMaterials), qCharges, money(pricing.operatingCost()));
                    })
                    .toList()
                : List.of();

        return new ProjectDtos.Response(p.getId(), p.getCode(), p.getName(),
                p.getQuoteId(), quoteNumber, p.getClientId(), clientName,
                p.getCostCenterId(), p.getCurrency(), p.getStatus(),
                p.getStartDate(), p.getEndDate(), p.getNotes(),
                contracted, executed, committed, invoiced, collected, margin, projected, marginPct,
                contracted.subtract(invoiced),
                contracted.subtract(collected),
                // Nunca negativo: facturar de más no es "costo sin facturar",
                // es margen, y ya se ve en `margin`.
                executed.subtract(invoiced).max(BigDecimal.ZERO),
                p.getCloseNote(),
                // Gastos legacy (project_cost) retirados: los gastos se ven
                // derivados de las cotizaciones. La lista queda vacía.
                List.of(),
                withCosts ? paid.stream().map(x -> new ProjectDtos.PaymentResponse(
                        x.getId(), x.getAmount(), x.getPaymentDate(),
                        x.getMethod(), x.getReference(),
                        x.getReceiptNumber(), x.getReceiptPrintedAt(), x.getQuoteId())).toList() : List.of(),
                money(materialsCost),
                money(materialsCostApproved),
                money(materialsCostTentative),
                quoteRefs,
                quoteCount);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Estados de cotización que cuentan como firmes para el costo de materiales. */
    private static boolean approvedQuoteStatus(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase();
        return s.equals("aprobada") || s.equals("convertida");
    }

    private BigDecimal plannedMaterialAmount(ProjectMaterial material) {
        BigDecimal unitCost = material.getUnitCostSnapshot() == null
                ? BigDecimal.ZERO : material.getUnitCostSnapshot();
        BigDecimal quantity = material.getQuantityPlanned() == null
                ? BigDecimal.ZERO : material.getQuantityPlanned();
        return unitCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
}
