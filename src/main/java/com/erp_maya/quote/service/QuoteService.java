package com.erp_maya.quote.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.settings.service.TaxService;
import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.repository.ClientRepository;
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
import java.util.List;
import java.util.Optional;

@Singleton
public class QuoteService {

    private final QuoteRepository quotes;
    private final QuoteHistoryRepository history;
    private final ProductRepository products;
    private final ClientRepository clients;
    private final TaxService taxService;
    private final TenantContext tenant;

    public QuoteService(QuoteRepository quotes, QuoteHistoryRepository history, ProductRepository products,
                        ClientRepository clients, TenantContext tenant,
                       TaxService taxService) {
        this.quotes = quotes;
        this.history = history;
        this.products = products;
        this.clients = clients;
        this.taxService = taxService;
        this.tenant = tenant;
    }

    @Transactional
    public Page<QuoteDtos.Response> list(String partyType, Pageable pageable) {
        Long companyId = tenant.getCompanyId();
        Page<Quote> page = (partyType == null || partyType.isBlank())
                ? quotes.findByCompanyIdOrderByQuoteDateDesc(companyId, pageable)
                : quotes.findByCompanyIdAndPartyTypeOrderByQuoteDateDesc(companyId, partyType, pageable);
        return page.map(q -> toResponse(q, List.of()));
    }

    @Transactional
    public QuoteDtos.Response get(Long id) {
        Quote quote = quotes.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
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
        quote.setStatus(rfq ? "solicitada" : "borrador");
        // Cliente de la cotización. Sin esto se guardaba como texto suelto y
        // nunca quedaba asociada, que es lo que impedía convertirla en proyecto.
        if (!rfq) {
            resolveOrCreateClient(companyId, req).ifPresent(quote::setClient);
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
        BigDecimal tax = total.multiply(TaxService.factorOverGross(rate)).setScale(2, RoundingMode.HALF_UP);
        quote.setTaxRate(rate);
        quote.setTotal(total);
        quote.setTax(tax);
        quote.setSubtotal(total.subtract(tax));

        Quote saved = quotes.save(quote);
        history.save(new QuoteHistory(companyId, saved.getId(),
                rfq ? "Solicitud de cotización creada" : "Cotización creada", req.createdBy()));
        return toResponse(saved, history.findByQuoteIdOrderByCreatedAtAsc(saved.getId()));
    }

    @Transactional
    public QuoteDtos.Response updateStatus(Long id, QuoteDtos.StatusRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote quote = quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        quote.setStatus(req.status());
        Quote saved = quotes.update(quote);
        String action = req.note() != null && !req.note().isBlank() ? req.note() : "Estado → " + req.status();
        history.save(new QuoteHistory(companyId, saved.getId(), action, req.actor()));
        return toResponse(saved, history.findByQuoteIdOrderByCreatedAtAsc(saved.getId()));
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
                q.getSubtotal(), q.getTax(), q.getTaxRate(), q.getTotal(), q.getStatus(), q.getNotes(),
                items, historyOut);
    }
}
