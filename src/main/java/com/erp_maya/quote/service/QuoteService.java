package com.erp_maya.quote.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.sequence.service.DocumentSequenceService;
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
import com.erp_maya.quote.repository.QuoteNotificationRepository;
import com.erp_maya.quote.domain.QuoteNotification;
import io.micronaut.serde.ObjectMapper;
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

    private final DocumentSequenceService sequences;
    private final QuoteNotificationRepository avisos;
    private final ObjectMapper json;

    /** Canal por el que entró la conversación cuando la cotización la armó el asistente. */
    private static final String CANAL_WHATSAPP = "whatsapp";
    private static final String AVISO_ENVIADA = "cotizacion_enviada";

    /** Origen de las cotizaciones que crea el asistente comercial. */
    public static final String ORIGEN_AGENTE = "agente";
    /** Lo que el asistente arma con el cliente: solo el asistente la edita. */
    public static final String ESTADO_ABIERTA = "abierta";
    /** El cliente la dio por terminada; espera que un vendedor la abra. */
    public static final String ESTADO_PROSPECTO = "prospecto";
    /** Un vendedor la abrió: desde aquí es suya. */
    public static final String ESTADO_BORRADOR = "borrador";

    public QuoteService(QuoteRepository quotes, QuoteHistoryRepository history, ProductRepository products,
                        ClientRepository clients, Projects projects, ProjectQuoteRepository projectQuotes,
                        Materials projectMaterials, TenantContext tenant, TaxService taxService,
                        QuoteChargeService quoteCharges, QuotePlanService paymentPlans,
                        DocumentSequenceService sequences, QuoteNotificationRepository avisos, ObjectMapper json) {
        this.sequences = sequences;
        this.avisos = avisos;
        this.json = json;
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
        // Las que el asistente todavía arma con el cliente no se listan: no
        // son trabajo del vendedor hasta que el cliente las termine.
        Page<Quote> page = (partyType == null || partyType.isBlank())
                ? quotes.findByCompanyIdAndStatusNotEqualOrderByQuoteDateDesc(companyId, ESTADO_ABIERTA, pageable)
                : quotes.findByCompanyIdAndPartyTypeAndStatusNotEqualOrderByQuoteDateDesc(
                        companyId, partyType, ESTADO_ABIERTA, pageable);
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
                ? req.docNumber() : sequences.next(rfq ? "RFQ" : "COT", "A"));
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
        // Lo que crea el asistente nace 'abierta': la arma con el cliente y
        // pasa a prospecto cuando el cliente la da por terminada.
        boolean deAgente = !rfq && ORIGEN_AGENTE.equalsIgnoreCase(req.origin());
        quote.setStatus(rfq ? "solicitada" : deAgente ? ESTADO_ABIERTA : ESTADO_BORRADOR);
        quote.setOrigin(deAgente ? ORIGEN_AGENTE : null);
        quote.setChannel(deAgente ? req.channel() : null);
        quote.setConversationRef(deAgente ? req.conversationRef() : null);
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
        if (!esEditable(quote.getStatus())) {
            throw new IllegalStateException("Solo se pueden editar cotizaciones en borrador o prospecto");
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
        // Renglones nuevos (sin id): se agregan DESPUÉS de podar los que no
        // vinieron, porque aún no tienen id y la poda los quitaría.
        var nuevos = new java.util.ArrayList<QuoteItem>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (QuoteDtos.UpdateItemRequest itemReq : req.items()) {
            QuoteItem item;
            if (itemReq.id() == null) {
                item = new QuoteItem();
                if (itemReq.productId() != null) {
                    Product product = products.findByIdAndCompanyId(itemReq.productId(), companyId)
                            .orElseThrow(() -> new ResourceNotFoundException("Producto " + itemReq.productId() + " no encontrado"));
                    item.setProduct(product);
                } else if (itemReq.description() == null || itemReq.description().isBlank()) {
                    throw new IllegalStateException("Una línea nueva necesita producto o descripción");
                }
                item.setUom(itemReq.uom());
                nuevos.add(item);
            } else {
                item = itemsById.get(itemReq.id());
                if (item == null) {
                    throw new IllegalStateException("La línea " + itemReq.id() + " no pertenece a la cotización");
                }
                keptItemIds.add(item.getId());
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
        nuevos.forEach(quote::addItem);

        BigDecimal taxRate = quoteCharges.rateFor(quote);
        BigDecimal tax = subtotal.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        quote.setTaxRate(taxRate);
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
        boolean pasaAEnviada = "enviada".equalsIgnoreCase(req.status()) && !"enviada".equalsIgnoreCase(quote.getStatus());
        if (pasaAEnviada) {
            validateExpirationBeforeClientDelivery(quote);
            paymentPlans.validateReadyToSend(id, companyId);
        }
        quote.setStatus(req.status());
        // Cada envío es una versión: la decisión del cliente se ata a la que vio.
        if (pasaAEnviada) quote.setSentVersion(quote.getSentVersion() + 1);
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
        // Si la pidió por WhatsApp, también le llega por ahí (además del correo,
        // que manda el controlador). En la misma transacción: si el cambio de
        // estado no se guarda, tampoco queda el aviso.
        if (pasaAEnviada && esDeWhatsapp(saved)) encolarEnviadaPorWhatsapp(saved, false);
        return toResponse(saved, history.findByQuoteIdOrderByCreatedAtAsc(saved.getId()));
    }

    /** Solo las que nacieron en una conversación de WhatsApp pueden avisarse por ahí. */
    public static boolean esDeWhatsapp(Quote q) {
        return CANAL_WHATSAPP.equalsIgnoreCase(q.getChannel())
                && q.getConversationRef() != null && !q.getConversationRef().isBlank();
    }

    /**
     * El vendedor vuelve a mandar por WhatsApp la cotización enviada: mismo
     * PDF y botones de la versión VIGENTE (no sube la versión, así un botón que
     * el cliente ya tenía sigue valiendo). Solo si nació por WhatsApp.
     */
    @Transactional
    public QuoteDtos.Response reenviarPorWhatsapp(Long id, String actor) {
        Long companyId = tenant.getCompanyId();
        Quote q = quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        if (!esDeWhatsapp(q)) {
            throw new IllegalStateException("La cotización " + q.getDocNumber() + " no se creó por WhatsApp");
        }
        if (!"enviada".equalsIgnoreCase(q.getStatus())) {
            throw new IllegalStateException("Solo se reenvía una cotización enviada (está " + q.getStatus() + ")");
        }
        validateExpirationBeforeClientDelivery(q);
        encolarEnviadaPorWhatsapp(q, true);
        history.save(new QuoteHistory(companyId, q.getId(),
                "Reenviada por WhatsApp (versión " + q.getSentVersion() + ")", actor));
        return toResponse(q, history.findByQuoteIdOrderByCreatedAtAsc(q.getId()));
    }

    private void encolarEnviadaPorWhatsapp(Quote q, boolean reenvio) {
        var payload = new QuoteDtos.QuoteSentPayload(q.getId(), q.getDocNumber(), q.getClientName(),
                "Q " + new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
                        .format((q.getTotal() == null ? BigDecimal.ZERO : q.getTotal()).setScale(2, RoundingMode.HALF_UP)),
                "/api/quotes/" + q.getId() + "/pdf", q.getSentVersion(), reenvio);
        QuoteNotification n = new QuoteNotification();
        n.setCompanyId(q.getCompanyId());
        n.setQuoteId(q.getId());
        n.setKind(AVISO_ENVIADA);
        n.setChannel(q.getChannel());
        n.setConversationRef(q.getConversationRef());
        try {
            n.setPayload(json.writeValueAsString(payload));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo armar el aviso de envío al cliente", e);
        }
        avisos.save(n);
    }

    // ── Decisión del cliente sobre la cotización enviada ───────────────────

    /** Motivos de rechazo del cliente. Opcionales: nunca se le exigen. */
    public static final List<QuoteDtos.ClientReason> MOTIVOS_CLIENTE = List.of(
            new QuoteDtos.ClientReason("precio", "Precio"),
            new QuoteDtos.ClientReason("plazo_entrega", "Plazo de entrega"),
            new QuoteDtos.ClientReason("compro_en_otro_lugar", "Compró en otro lugar"),
            new QuoteDtos.ClientReason("ya_no_lo_necesita", "Ya no lo necesita"),
            new QuoteDtos.ClientReason("otro", "Otro"));

    /**
     * El cliente aprueba la versión que se le envió. Solo si la cotización
     * sigue 'enviada', en ESA versión y vigente: así nunca aprueba algo
     * distinto de lo que vio.
     */
    @Transactional
    public QuoteDtos.Response aprobarPorCliente(Long id, QuoteDtos.ClientDecisionRequest req) {
        Quote q = paraDecision(id, req.version());
        if (q.getValidUntil() != null && q.getValidUntil().isBefore(LocalDate.now())) {
            history.save(new QuoteHistory(q.getCompanyId(), id,
                    "El cliente intentó aprobarla vencida (válida hasta " + q.getValidUntil() + "): hay que renovarla", req.actor()));
            throw new IllegalStateException("La cotización " + q.getDocNumber() + " venció el " + q.getValidUntil()
                    + "; tu asesor debe renovarla");
        }
        q.setClientDecidedAt(Instant.now());
        quotes.update(q);
        return updateStatus(id, new QuoteDtos.StatusRequest("aprobada",
                "Aprobada por el cliente (versión " + q.getSentVersion() + ")", req.actor()));
    }

    /** El cliente rechaza la versión enviada. El motivo es opcional. */
    @Transactional
    public QuoteDtos.Response rechazarPorCliente(Long id, QuoteDtos.ClientDecisionRequest req) {
        Quote q = paraDecision(id, req.version());
        q.setClientDecidedAt(Instant.now());
        aplicarMotivo(q, req.reasonCode(), req.note());
        quotes.update(q);
        String nota = "Rechazada por el cliente (versión " + q.getSentVersion() + ")"
                + (q.getClientReasonCode() != null ? " · motivo: " + etiquetaMotivo(q.getClientReasonCode()) : "");
        return updateStatus(id, new QuoteDtos.StatusRequest("rechazada", nota, req.actor()));
    }

    /** El cliente cuenta el motivo después de rechazar. Solo sobre una rechazada. */
    @Transactional
    public QuoteDtos.Response motivoDelCliente(Long id, QuoteDtos.ClientReasonRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote q = quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        if (!"rechazada".equalsIgnoreCase(q.getStatus())) {
            throw new IllegalStateException("La cotización " + q.getDocNumber() + " no está rechazada");
        }
        aplicarMotivo(q, req.reasonCode(), req.note());
        quotes.update(q);
        history.save(new QuoteHistory(companyId, id, "Motivo del rechazo: " + etiquetaMotivo(q.getClientReasonCode())
                + (q.getClientReasonNote() != null ? " — " + q.getClientReasonNote() : ""), null));
        return get(id);
    }

    private Quote paraDecision(Long id, Integer version) {
        Long companyId = tenant.getCompanyId();
        Quote q = quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        if (!"enviada".equalsIgnoreCase(q.getStatus())) {
            throw new IllegalStateException("La cotización " + q.getDocNumber() + " está " + q.getStatus()
                    + ": no espera una decisión del cliente");
        }
        if (version == null || version != q.getSentVersion()) {
            throw new IllegalStateException("VERSION_ANTERIOR: la cotización " + q.getDocNumber()
                    + " tiene una versión más reciente (v" + q.getSentVersion() + ")");
        }
        return q;
    }

    private void aplicarMotivo(Quote q, String codigo, String nota) {
        if (codigo != null && !codigo.isBlank()) {
            boolean valido = MOTIVOS_CLIENTE.stream().anyMatch(m -> m.code().equals(codigo));
            q.setClientReasonCode(valido ? codigo : "otro");
        }
        if (nota != null && !nota.isBlank()) q.setClientReasonNote(nota.trim());
    }

    private static String etiquetaMotivo(String codigo) {
        return MOTIVOS_CLIENTE.stream().filter(m -> m.code().equals(codigo)).map(QuoteDtos.ClientReason::label)
                .findFirst().orElse(codigo == null ? "sin motivo" : codigo);
    }

    // ── Ciclo de vida de las cotizaciones del asistente ─────────────────────

    /** abierta → prospecto: el cliente la dio por terminada. */
    @Transactional
    public QuoteDtos.Response finalizar(Long id, String actor) {
        Long companyId = tenant.getCompanyId();
        if (quotes.updateStatusSiEsta(id, companyId, ESTADO_ABIERTA, ESTADO_PROSPECTO) == 0) {
            Quote q = quotes.findByIdAndCompanyId(id, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
            if (ESTADO_PROSPECTO.equalsIgnoreCase(q.getStatus())) return get(id); // ya estaba: idempotente
            throw new IllegalStateException("La cotización " + q.getDocNumber() + " no está abierta (" + q.getStatus() + ")");
        }
        history.save(new QuoteHistory(companyId, id, "El cliente la dio por terminada: pasa a revisión", actor));
        return get(id);
    }

    /**
     * prospecto → abierta: el cliente quiere cambiar algo y ningún vendedor la
     * abrió todavía. Si ya la tomó un vendedor no se puede: el cliente deja
     * solicitudes de cambio.
     */
    @Transactional
    public QuoteDtos.Response reabrir(Long id, String actor) {
        Long companyId = tenant.getCompanyId();
        if (quotes.updateReabrirSiNoTomada(id, companyId) == 0) {
            Quote q = quotes.findByIdAndCompanyId(id, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
            if (ESTADO_ABIERTA.equalsIgnoreCase(q.getStatus())) return get(id);
            throw new IllegalStateException("La cotización " + q.getDocNumber()
                    + " ya está en manos de un vendedor; los cambios van como solicitud");
        }
        history.save(new QuoteHistory(companyId, id, "Reabierta a pedido del cliente", actor));
        return get(id);
    }

    /**
     * Un vendedor abre el prospecto: pasa a borrador y queda a su nombre. Si
     * no es un prospecto sin tomar, no hace nada (abrirla otra vez, o una
     * cotización normal).
     */
    @Transactional
    public QuoteDtos.Response tomar(Long id, String actor) {
        Long companyId = tenant.getCompanyId();
        if (quotes.updateTomar(id, companyId, actor, Instant.now()) > 0) {
            history.save(new QuoteHistory(companyId, id, "Tomada para revisión", actor));
        }
        return get(id);
    }

    /** Las últimas cotizaciones de un cliente, para que el asistente informe su estado. */
    @Transactional
    public List<QuoteDtos.ClientQuoteSummary> delCliente(Long clientId, int limite) {
        Long companyId = tenant.getCompanyId();
        return quotes.findByCliente(companyId, clientId, Pageable.from(0, Math.max(1, Math.min(limite, 20))))
                .stream()
                .map(q -> new QuoteDtos.ClientQuoteSummary(q.getId(), q.getDocNumber(), q.getStatus(),
                        q.getTotal(), q.getTakenAt() != null, q.getQuoteDate(), q.getUpdatedAt(),
                        q.getSentVersion(), q.getValidUntil()))
                .toList();
    }

    /** Anota en la bitácora sin tocar el estado ni nada más de la cotización. */
    public void addNote(Long id, QuoteDtos.NoteRequest req) {
        Long companyId = tenant.getCompanyId();
        quotes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + id + " no encontrada"));
        history.save(new QuoteHistory(companyId, id, req.note(), req.actor()));
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
                    project.setCode(sequences.next("PRY", "A"));
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
                items, historyOut,
                q.getOrigin(), q.getChannel(), q.getConversationRef(),
                q.getTakenBy(), q.getTakenAt(),
                q.getSentVersion(), q.getClientReasonCode(), q.getClientReasonNote());
    }

    /** Estados en los que el documento todavía se arma y se puede editar. */
    private static boolean esEditable(String status) {
        return ESTADO_BORRADOR.equalsIgnoreCase(status) || "draft".equalsIgnoreCase(status)
                || ESTADO_PROSPECTO.equalsIgnoreCase(status) || ESTADO_ABIERTA.equalsIgnoreCase(status);
    }
}
