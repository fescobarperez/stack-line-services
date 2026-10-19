package com.erp_maya.template.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.domain.ProjectMaterialGroup;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Groups;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteHistory;
import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.dto.QuoteDtos;
import com.erp_maya.quote.repository.QuoteHistoryRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.quote.service.QuoteChargeService;
import com.erp_maya.quote.service.QuoteService;
import com.erp_maya.template.domain.ProductTemplate;
import com.erp_maya.template.dto.TemplateDtos;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Una línea de cotización calculada con la plantilla de su producto.
 *
 * La línea queda con el costo de su materia prima como precio base (la
 * ganancia, el gasto operativo y el IVA los pone la cotización, como a
 * cualquier línea). El desglose entra al proyecto de la cotización en
 * project_materials, estampado con la cotización y la línea: de ahí la
 * cotización ya saca su costo y el proyecto su lista de compras al aprobarse.
 * La mano de obra y otros costos los agrega el asesor en la cotización.
 *
 * Recalcular reemplaza el desglose de esa línea; quitar la línea en la
 * edición de la cotización se lo lleva (QuoteService.update).
 */
@Singleton
public class QuoteTemplateService {

    static final String PRECIO_PLANTILLA = "template";
    static final String POR_REVISAR = "review";

    private final QuoteRepository quotes;
    private final QuoteHistoryRepository history;
    private final QuoteService quoteService;
    private final QuoteChargeService quoteCharges;
    private final TemplateService templates;
    private final ProductRepository products;
    private final Materials materials;
    private final Groups groups;
    private final TenantContext tenant;
    private final ObjectMapper json;

    public QuoteTemplateService(QuoteRepository quotes, QuoteHistoryRepository history, QuoteService quoteService,
                                QuoteChargeService quoteCharges, TemplateService templates,
                                ProductRepository products, Materials materials, Groups groups,
                                TenantContext tenant, ObjectMapper json) {
        this.quotes = quotes;
        this.history = history;
        this.quoteService = quoteService;
        this.quoteCharges = quoteCharges;
        this.templates = templates;
        this.products = products;
        this.materials = materials;
        this.groups = groups;
        this.tenant = tenant;
        this.json = json;
    }

    @Transactional
    public QuoteDtos.Response addItem(Long quoteId, TemplateDtos.QuoteItemRequest req, String actor) {
        Long companyId = tenant.getCompanyId();
        Quote quote = editable(quoteId, companyId);
        ProductTemplate t = templates.buscar(req.templateId());
        Product producto = products.findByIdAndCompanyId(t.getProductId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + t.getProductId() + " no encontrado"));
        BigDecimal cantidad = cantidad(req.quantity());
        TemplateCalculator.Result r = templates.calcular(t, req.params(), req.choices(), req.preset());

        QuoteItem item = new QuoteItem();
        item.setProduct(producto);
        llenar(item, t, producto, r, cantidad, req.preset());
        quote.addItem(item);
        // La línea necesita id para estampar sus materiales y cargos.
        quotes.flush();
        desglosar(quote, item, t, r, cantidad, companyId);

        history.save(new QuoteHistory(companyId, quoteId, "Línea calculada con plantilla: " + r.description(), actor));
        quoteCharges.getSummary(quoteId);
        return quoteService.get(quoteId);
    }

    @Transactional
    public QuoteDtos.Response recalculate(Long quoteId, Long itemId, TemplateDtos.QuoteItemRequest req, String actor) {
        Long companyId = tenant.getCompanyId();
        Quote quote = editable(quoteId, companyId);
        QuoteItem item = quote.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La línea " + itemId + " no es de esta cotización"));
        if (item.getTemplateId() == null) {
            throw new IllegalStateException("Esa línea no se calculó con plantilla");
        }
        ProductTemplate t = templates.buscar(req.templateId());
        Product producto = products.findByIdAndCompanyId(t.getProductId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + t.getProductId() + " no encontrado"));
        BigDecimal cantidad = cantidad(req.quantity() != null ? req.quantity() : item.getQuantity());
        TemplateCalculator.Result r = templates.calcular(t, req.params(), req.choices(), req.preset());

        limpiar(item, companyId);
        item.setProduct(producto);
        llenar(item, t, producto, r, cantidad, req.preset());
        desglosar(quote, item, t, r, cantidad, companyId);

        history.save(new QuoteHistory(companyId, quoteId, "Línea recalculada con plantilla: " + r.description(), actor));
        quoteCharges.getSummary(quoteId);
        return quoteService.get(quoteId);
    }

    // ── piezas ────────────────────────────────────────────────────────

    private Quote editable(Long quoteId, Long companyId) {
        Quote quote = quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
        if (!"client".equalsIgnoreCase(quote.getPartyType())) {
            throw new IllegalStateException("Las plantillas son para cotizaciones a clientes");
        }
        if (!QuoteService.esEditable(quote.getStatus())) {
            throw new IllegalStateException("Solo se pueden editar cotizaciones en borrador o prospecto");
        }
        if (quote.getProjectId() == null) {
            throw new IllegalStateException("La cotización no tiene proyecto donde dejar su materia prima");
        }
        return quote;
    }

    /** La línea: costo de materia prima como precio base, medidas y la configuración para recalcular. */
    void llenar(QuoteItem item, ProductTemplate t, Product producto, TemplateCalculator.Result r,
                BigDecimal cantidad, String preset) {
        String desc = r.description();
        item.setItemName(desc.length() > 200 ? desc.substring(0, 199) + "…" : desc);
        item.setDescription(desc);
        item.setUom(producto.getUnit() == null || producto.getUnit().isBlank() ? "unid" : producto.getUnit());
        item.setQuantity(cantidad);
        item.setUnitPrice(r.materialsCost());
        item.setDiscount(BigDecimal.ZERO);
        item.setLineTotal(r.materialsCost().multiply(cantidad).setScale(2, RoundingMode.HALF_UP));
        // Medidas con los nombres de siempre; la de catálogo igual, para que no
        // se lea como «medida especial».
        item.setWidth(r.params().get("ancho"));
        item.setHeight(r.params().get("alto"));
        item.setDepth(r.params().get("profundidad"));
        item.setBaseWidth(item.getWidth());
        item.setBaseHeight(item.getHeight());
        item.setBaseDepth(item.getDepth());
        item.setShowToClient(Boolean.TRUE);
        // Sin costo en alguna materia prima el precio no es confiable: lo revisa el asesor.
        item.setSizePricing(r.missingCosts() ? POR_REVISAR : PRECIO_PLANTILLA);
        item.setTemplateId(t.getId());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("version", t.getVersion());
        config.put("params", r.params());
        config.put("choices", r.choices());
        if (preset != null && !preset.isBlank()) config.put("preset", preset.trim());
        config.put("renderText", r.renderText());
        item.setTemplateConfig(escribir(config));
    }

    /** Materia prima al proyecto: un grupo por línea y subgrupos por parte (Estructura, Techo…). */
    void desglosar(Quote quote, QuoteItem item, ProductTemplate t, TemplateCalculator.Result r,
                   BigDecimal cantidad, Long companyId) {
        ProjectMaterialGroup raiz = new ProjectMaterialGroup();
        raiz.setCompanyId(companyId);
        raiz.setProjectId(quote.getProjectId());
        raiz.setName(recortar(r.description(), 160));
        raiz.setSortOrder(0);
        raiz = groups.save(raiz);
        item.setSourceGroupId(raiz.getId());

        Map<String, Long> subgrupos = new LinkedHashMap<>();
        int orden = 0;
        String nota = recortar("Plantilla " + t.getName() + " v" + t.getVersion(), 500);
        for (TemplateCalculator.Line l : r.lines()) {
            Long grupo = subgrupos.get(l.group());
            if (grupo == null) {
                ProjectMaterialGroup g = new ProjectMaterialGroup();
                g.setCompanyId(companyId);
                g.setProjectId(quote.getProjectId());
                g.setParentGroupId(raiz.getId());
                g.setName(recortar(l.group(), 160));
                g.setSortOrder(orden++);
                grupo = groups.save(g).getId();
                subgrupos.put(l.group(), grupo);
            }
            ProjectMaterial m = new ProjectMaterial();
            m.setCompanyId(companyId);
            m.setProjectId(quote.getProjectId());
            m.setGroupId(grupo);
            m.setQuoteId(quote.getId());
            m.setQuoteItemId(item.getId());
            m.setProductId(l.productId());
            m.setSupplierId(l.supplierId());
            m.setQuantityPlanned(l.quantity().multiply(cantidad));
            m.setUom(l.unit());
            m.setUnitCostSnapshot(l.unitCost());
            m.setCostSnapshotAt(Instant.now());
            m.setNotes(nota);
            materials.save(m);
        }
    }

    /** Quita el desglose anterior de la línea. */
    private void limpiar(QuoteItem item, Long companyId) {
        materials.deleteAll(materials.findByCompanyIdAndQuoteItemId(companyId, item.getId()));
        if (item.getSourceGroupId() != null) {
            // Los subgrupos caen en cascada (parent_group_id ON DELETE CASCADE).
            groups.findByIdAndCompanyId(item.getSourceGroupId(), companyId).ifPresent(groups::delete);
            item.setSourceGroupId(null);
        }
    }

    static BigDecimal cantidad(BigDecimal pedida) {
        BigDecimal c = pedida == null ? BigDecimal.ONE : pedida;
        if (c.signum() <= 0) throw new IllegalStateException("La cantidad debe ser mayor que cero");
        return c;
    }

    private static String recortar(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String escribir(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
