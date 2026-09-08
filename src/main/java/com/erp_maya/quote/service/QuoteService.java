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
        LocalDate quoteDate = req.quoteDate() != null ? req.quoteDate() : LocalDate.now();
        if (!rfq && req.validUntil() == null) {
            throw new IllegalStateException("Una cotización a cliente necesita fecha de expiración");
        }
        if (!rfq && req.validUntil().isBefore(quoteDate)) {
            throw new IllegalStateException("La fecha de expiración no puede ser anterior a la fecha de cotización");
        }
        quote.setQuoteDate(quoteDate);
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
    public QuoteDtos.Response update(Long id, QuoteDtos.UpdateRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote quote = quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        if (!"borrador".equalsIgnoreCase(quote.getStatus()) && !"draft".equalsIgnoreCase(quote.getStatus())) {
            throw new IllegalStateException("Solo se pueden editar cotizaciones en borrador");
        }

        LocalDate quoteDate = quote.getQuoteDate() != null ? quote.getQuoteDate() : LocalDate.now();
        if (req.validUntil() == null) {
            throw new IllegalStateException("Una cotización a cliente necesita fecha de expiración");
        }
        if (req.validUntil().isBefore(quoteDate)) {
            throw new IllegalStateException("La fecha de expiración no puede ser anterior a la fecha de cotización");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalStateException("La cotización debe conservar al menos una línea");
        }

        quote.setValidUntil(req.validUntil());
        quote.setNotes(req.notes());
        quote.setProfitCalcType("percent".equalsIgnoreCase(req.profitCalcType()) ? "percent" : "fixed");
        quote.setProfitValue(req.profitValue() != null ? req.profitValue() : BigDecimal.ZERO);

        var itemsById = quote.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(QuoteItem::getId, java.util.function.Function.identity()));
        var keptItemIds = new java.util.HashSet<Long>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (QuoteDtos.UpdateItemRequest itemReq : req.items()) {
            QuoteItem item = itemsById.get(itemReq.id());
            if (item == null) {
                throw new IllegalStateException("La línea " + itemReq.id() + " no pertenece a la cotización");
            }
            if (itemReq.quantity() == null || itemReq.quantity().signum() <= 0) {
                throw new IllegalStateException("La cantidad de cada línea debe ser mayor que cero");
            }
            BigDecimal unitPrice = itemReq.unitPrice() != null ? itemReq.unitPrice() : BigDecimal.ZERO;
            BigDecimal discount = itemReq.discount() != null ? itemReq.discount() : BigDecimal.ZERO;
            if (unitPrice.signum() < 0 || discount.signum() < 0 || discount.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalStateException("Precio y descuento deben ser valores válidos");
            }
            BigDecimal factor = BigDecimal.ONE.subtract(discount.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
            BigDecimal lineTotal = unitPrice.multiply(itemReq.quantity()).multiply(factor).setScale(2, RoundingMode.HALF_UP);
            item.setItemName(itemReq.description());
            item.setDescription(itemReq.description());
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(unitPrice);
            item.setDiscount(discount);
            item.setLineTotal(lineTotal);
            keptItemIds.add(item.getId());
            subtotal = subtotal.add(lineTotal);
        }

        for (ProjectMaterial material : projectMaterials.findByCompanyIdAndQuoteId(companyId, quote.getId())) {
            if (material.getQuoteItemId() != null && !keptItemIds.contains(material.getQuoteItemId())) {
                material.setQuoteId(null);
                material.setQuoteItemId(null);
                projectMaterials.update(material);
            }
        }
        quote.getItems().removeIf(item -> !keptItemIds.contains(item.getId()));

        BigDecimal taxRate = quote.getTaxRate() != null ? quote.getTaxRate() : taxService.rate();
        BigDecimal tax = subtotal.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        quote.setQuoteDate(quoteDate);
        quote.setSubtotal(subtotal);
        quote.setTax(tax);
        quote.setTotal(subtotal.add(tax).setScale(2, RoundingMode.HALF_UP));
        Quote saved = quotes.update(quote);
        quoteCharges.getSummary(saved.getId());
        history.save(new QuoteHistory(companyId, saved.getId(), "Cotización editada", null));
        return toResponse(saved, history.findByQuoteIdOrderByCreatedAtAsc(saved.getId()));
    }

    @Transactional
    public QuoteDtos.Response updateStatus(Long id, QuoteDtos.StatusRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote quote = quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        if ("enviada".equalsIgnoreCase(req.status()) && !"enviada".equalsIgnoreCase(quote.getStatus())) {
            validateExpirationBeforeClientDelivery(quote);
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

    private void validateExpirationBeforeClientDelivery(Quote quote) {
        if (!"client".equalsIgnoreCase(quote.getPartyType())) return;

        LocalDate today = LocalDate.now();
        LocalDate quoteDate = quote.getQuoteDate() != null ? quote.getQuoteDate() : today;
        if (quote.getValidUntil() == null) {
            throw new IllegalStateException("No se puede enviar la cotización sin fecha de expiración");
        }
        if (quote.getValidUntil().isBefore(today)) {
            throw new IllegalStateException("No se puede enviar una cotización con fecha de expiración vencida");
        }
        if (quote.getValidUntil().isBefore(quoteDate)) {
            throw new IllegalStateException("La fecha de expiración no puede ser anterior a la fecha de cotización");
        }
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
                i.getProduct() != null ? i.getProduct().getName()
                        : (i.getDescription() != null && !i.getDescription().isBlank() ? i.getDescription() : i.getItemName()),
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
