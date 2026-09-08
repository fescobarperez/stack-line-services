package com.erp_maya.quote.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.settings.service.TaxService;
import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.project.domain.Project;
import com.erp_maya.project.domain.ProjectQuote;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.project.repository.ProjectQuoteRepository;
import com.erp_maya.project.repository.ProjectRepositories.Projects;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteHistory;
import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.dto.QuoteDtos;
import com.erp_maya.quote.repository.QuoteHistoryRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Singleton
public class QuoteService {

    private final QuoteRepository quotes;
    private final QuoteHistoryRepository history;
    private final ProductRepository products;
    private final ClientRepository clients;
    private final Projects projects;
    private final ProjectQuoteRepository projectQuotes;
    private final Materials projectMaterials;
    private final TaxService taxService;
    private final QuoteChargeService quoteCharges;
    private final QuotePlanService paymentPlans;
    private final TenantContext tenant;

    public QuoteService(QuoteRepository quotes, QuoteHistoryRepository history, ProductRepository products,
                        ClientRepository clients, Projects projects, ProjectQuoteRepository projectQuotes,
                        Materials projectMaterials, TenantContext tenant, TaxService taxService,
                        QuoteChargeService quoteCharges, QuotePlanService paymentPlans) {
        this.quotes = quotes;
        this.history = history;
        this.products = products;
        this.clients = clients;
        this.projects = projects;
        this.projectQuotes = projectQuotes;
        this.projectMaterials = projectMaterials;
        this.taxService = taxService;
        this.quoteCharges = quoteCharges;
        this.paymentPlans = paymentPlans;
        this.tenant = tenant;
    }

    @Transactional
    public Page<QuoteDtos.Response> list(String partyType, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<Quote> page = (partyType == null || partyType.isBlank())
                ? quotes.findByCompanyIdOrderByQuoteDateDesc(companyId, pageable)
                : quotes.findByCompanyIdAndPartyTypeOrderByQuoteDateDesc(companyId, partyType, pageable);
        return page.map(q -> {
            if ("client".equalsIgnoreCase(q.getPartyType())) quoteCharges.getSummary(q.getId());
            return toResponse(q, List.of());
        });
    }

    @Transactional
    public QuoteDtos.Response get(Long id) {
        Quote quote = quotes.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        if ("client".equalsIgnoreCase(quote.getPartyType())) quoteCharges.getSummary(quote.getId());
        return toResponse(quote, history.findByQuoteIdOrderByCreatedAtAsc(quote.getId()));
    }

    @Transactional
    public QuoteDtos.Response create(QuoteDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        String partyType = req.partyType() != null && !req.partyType().isBlank() ? req.partyType() : "client";
        boolean rfq = "supplier".equalsIgnoreCase(partyType);

        Quote quote = new Quote();
        quote.setCompanyId(companyId);
        quote.setPartyType(partyType);
        quote.setDocNumber(req.docNumber() != null && !req.docNumber().isBlank()
                ? req.docNumber() : (rfq ? "RFQ-" : "COT-") + Instant.now().toEpochMilli());
        quote.setClientName(req.clientName());
        quote.setClientNit(req.clientNit());
        quote.setClientEmail(req.clientEmail());
        quote.setClientContact(req.clientContact());
        quote.setSupplierName(req.supplierName());
        quote.setSupplierNit(req.supplierNit());
        quote.setSupplierEmail(req.supplierEmail());
        quote.setSupplierContact(req.supplierContact());
        quote.setQuoteDate(req.quoteDate());
        quote.setValidUntil(req.validUntil());
        quote.setDeadline(req.deadline());
        quote.setLeadTime(req.leadTime());
        quote.setPaymentTerms(req.paymentTerms());
        quote.setCreatedBy(req.createdBy());
        quote.setNotes(req.notes());
        quote.setProfitCalcType("percent".equalsIgnoreCase(req.profitCalcType()) ? "percent" : "fixed");
        quote.setProfitValue(req.profitValue() != null ? req.profitValue() : BigDecimal.ZERO);
        quote.setStatus(rfq ? "solicitada" : "borrador");
        Client quoteClient = null;
        // Toda cotización a cliente queda anclada a un proyecto. RFQ no participa
        // en proyectos: sigue siendo un documento de compras independiente.
        if (!rfq) {
            quoteClient = resolveOrCreateClient(companyId, req)
                    .orElseThrow(() -> new IllegalStateException(
                            "Una cotización cliente necesita un cliente para asignar su proyecto"));
            quote.setClient(quoteClient);
        } else if (req.clientId() != null) {
            clients.findByIdAndCompanyId(req.clientId(), companyId).ifPresent(quote::setClient);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (QuoteDtos.ItemRequest ir : req.items()) {
            BigDecimal unitPrice = ir.unitPrice() != null ? ir.unitPrice() : BigDecimal.ZERO;
            BigDecimal discount = ir.discount() != null ? ir.discount() : BigDecimal.ZERO; // % de descuento
            BigDecimal factor = BigDecimal.ONE.subtract(discount.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
            BigDecimal lineTotal = unitPrice.multiply(ir.quantity()).multiply(factor).setScale(2, RoundingMode.HALF_UP);
            QuoteItem item = new QuoteItem();
            if (ir.productId() != null) {
                Product product = products.findByIdAndCompanyId(ir.productId(), companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Producto " + ir.productId() + " no encontrado"));
                item.setProduct(product);
            }
            item.setItemName(ir.itemName());
            item.setUom(ir.uom());
            item.setQuantity(ir.quantity());
            item.setUnitPrice(unitPrice);
            item.setDiscount(discount);
            item.setLineTotal(lineTotal);
            quote.addItem(item);
            total = total.add(lineTotal);
        }
        // La tasa sale de la configuración de la empresa, no de una constante.
        BigDecimal rate = taxService.rate();
        BigDecimal tax = total.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        quote.setTaxRate(rate);
        quote.setSubtotal(total);
        quote.setTax(tax);
        quote.setTotal(total.add(tax).setScale(2, RoundingMode.HALF_UP));

        if (!rfq) {
            Project project = resolveProject(companyId, req.projectId(), quoteClient);
            quote.setProjectId(project.getId());
        }

        Quote saved = quotes.save(quote);
        if (!rfq) {
            ProjectQuote link = new ProjectQuote();
            link.setCompanyId(companyId);
            link.setProjectId(saved.getProjectId());
            link.setQuoteId(saved.getId());
            link.setAmountSnapshot(saved.getTotal());
            link.setIncluded(Boolean.FALSE); // solo aprobada agrega al proyecto
            link.setCreatedAt(Instant.now());
            projectQuotes.save(link);
        }
        history.save(new QuoteHistory(companyId, saved.getId(),
                rfq ? "Solicitud de cotización creada" : "Cotización creada y anclada al proyecto", req.createdBy()));
        return toResponse(saved, history.findByQuoteIdOrderByCreatedAtAsc(saved.getId()));
    }

    @Transactional
    public QuoteDtos.Response updateStatus(Long id, QuoteDtos.StatusRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote quote = quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        if ("enviada".equalsIgnoreCase(req.status()) && !"enviada".equalsIgnoreCase(quote.getStatus())) {
            paymentPlans.validateReadyToSend(id, companyId);
        }
        quote.setStatus(req.status());
        Quote saved = quotes.update(quote);

        // Cotización rechazada/cancelada: sus materiales vuelven al pool
        // disponible del proyecto para que puedan ir en otra cotización.
        if (releasesMaterials(saved.getStatus())) {
            for (ProjectMaterial m : projectMaterials.findByCompanyIdAndQuoteId(companyId, saved.getId())) {
                m.setQuoteId(null);
                m.setQuoteItemId(null);
                projectMaterials.update(m);
            }
        }
        projectQuotes.findByCompanyIdAndQuoteId(companyId, saved.getId()).ifPresent(link -> {
            boolean included = contributesToProject(saved.getStatus());
            link.setIncluded(included);
            link.setAmountSnapshot(saved.getTotal());
            if (included) {
                link.setIncludedAt(Instant.now());
                link.setExcludedAt(null);
                link.setExclusionReason(null);
                projects.findByIdAndCompanyId(link.getProjectId(), companyId).ifPresent(project -> {
                    if ("draft".equals(project.getStatus())) {
                        project.setStatus("open");
                        projects.update(project);
                    }
                });
            } else {
                link.setExcludedAt(Instant.now());
                link.setExclusionReason(req.note());
            }
            projectQuotes.update(link);
        });
        String action = req.note() != null && !req.note().isBlank() ? req.note() : "Estado → " + req.status();
        history.save(new QuoteHistory(companyId, saved.getId(), action, req.actor()));
        return toResponse(saved, history.findByQuoteIdOrderByCreatedAtAsc(saved.getId()));
    }

    private Project resolveProject(Long companyId, Long projectId, Client client) {
        if (projectId != null) {
            Project selected = projects.findByIdAndCompanyId(projectId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Proyecto " + projectId + " no encontrado"));
            if (!selected.getClientId().equals(client.getId())) {
                throw new IllegalStateException("La cotización y el proyecto deben pertenecer al mismo cliente");
            }
            return selected;
        }

        // No se elige un proyecto existente al azar: eso mezclaría clientes y
        // rentabilidad. Se reutiliza uno estable por cliente para que el usuario
        // básico pueda cotizar sin entender todavía el concepto de proyecto.
        return projects.findByCompanyIdAndClientIdAndName(companyId, client.getId(), "Proyecto general")
                .orElseGet(() -> {
                    Project project = new Project();
                    project.setCompanyId(companyId);
                    project.setClientId(client.getId());
                    project.setCode("PRY-AUTO-" + (projects.countByCompanyId(companyId) + 1));
                    project.setName("Proyecto general");
                    project.setCurrency("GTQ");
                    project.setStatus("draft");
                    project.setStartDate(LocalDate.now());
                    return projects.save(project);
                });
    }

    private boolean contributesToProject(String status) {
        return "aprobada".equalsIgnoreCase(status) || "convertida".equalsIgnoreCase(status);
    }

    /** Estados en los que la cotización deja de reservar sus materiales. */
    private boolean releasesMaterials(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase();
        return s.equals("rechazada") || s.equals("cancelada") || s.equals("anulada") || s.equals("vencida");
    }

    /**
     * 1. Si viene clientId, ese manda.
     * 2. Si no, se busca por NIT: identifica al cliente sin depender de cómo
     *    se escribiera el nombre.
     * 3. Si tampoco existe, se crea con lo capturado en el formulario.
     */
    private Optional<Client> resolveOrCreateClient(Long companyId, QuoteDtos.Request req) {
        if (req.clientId() != null) {
            Optional<Client> byId = clients.findByIdAndCompanyId(req.clientId(), companyId);
            if (byId.isPresent()) return byId;
        }
        String nit = req.clientNit() == null ? "" : req.clientNit().trim();
        if (!nit.isBlank() && !"CF".equalsIgnoreCase(nit)) {
            Optional<Client> byNit = clients.findByCompanyIdAndNit(companyId, nit);
            if (byNit.isPresent()) return byNit;
        }
        // Sin nombre no hay cliente que crear; la cotización queda sin asociar.
        if (req.clientName() == null || req.clientName().isBlank()) return Optional.empty();

        Client c = new Client();
        c.setCompanyId(companyId);
        c.setName(req.clientName().trim());
        c.setNit(nit.isBlank() ? null : nit);
        c.setEmail(req.clientEmail());
        c.setPhone(req.clientContact());
        c.setClientType("Consumidor final");
        c.setStatus("active");
        return Optional.of(clients.save(c));
    }

    private static QuoteDtos.Response toResponse(Quote q, List<QuoteHistory> hist) {
        var items = q.getItems().stream().map(i -> new QuoteDtos.ItemResponse(
                i.getId(), i.getProduct() != null ? i.getProduct().getId() : null,
                i.getProduct() != null ? i.getProduct().getName() : i.getItemName(),
                i.getUom(), i.getQuantity(), i.getUnitPrice(), i.getDiscount(), i.getLineTotal())).toList();
        var historyOut = hist.stream().map(h -> new QuoteDtos.HistoryEntry(
                h.getId(), h.getAction(), h.getActor(), h.getCreatedAt())).toList();
        return new QuoteDtos.Response(q.getId(), q.getDocNumber(), q.getPartyType(),
                q.getClient() != null ? q.getClient().getId() : null, q.getClientName(), q.getClientNit(),
                q.getClientEmail(), q.getClientContact(),
                q.getSupplierName(), q.getSupplierNit(), q.getSupplierEmail(), q.getSupplierContact(),
                q.getQuoteDate(), q.getValidUntil(), q.getDeadline(),
                q.getLeadTime(), q.getPaymentTerms(), q.getCreatedBy(),
                q.getProjectId(),
                q.getSubtotal(), q.getTax(), q.getTaxRate(),
                q.getProfitCalcType(), q.getProfitValue(), q.getProfitAmount(), q.getTotal(), q.getStatus(), q.getNotes(),
                items, historyOut);
    }
}
