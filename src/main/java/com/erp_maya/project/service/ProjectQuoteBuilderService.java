package com.erp_maya.project.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.project.domain.Project;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.domain.ProjectMaterialGroup;
import com.erp_maya.project.domain.ProjectQuote;
import com.erp_maya.project.dto.ProjectQuoteBuilderDtos;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Groups;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.project.repository.ProjectQuoteRepository;
import com.erp_maya.project.repository.ProjectRepositories.Projects;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteHistory;
import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.repository.QuoteHistoryRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.settings.service.TaxService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Construye una cotización a partir de materiales del proyecto (Fase 1 del
 * modelo cotización-céntrico). La cotización es la unidad comercial; sus
 * líneas se componen de materiales del pool disponible del proyecto, que
 * quedan estampados con la cotización y salen del pool.
 */
@Singleton
public class ProjectQuoteBuilderService {

    private final Projects projects;
    private final Materials materials;
    private final Groups groups;
    private final QuoteRepository quotes;
    private final QuoteHistoryRepository quoteHistory;
    private final ProjectQuoteRepository projectQuotes;
    private final ClientRepository clients;
    private final TaxService taxService;
    private final TenantContext tenant;

    public ProjectQuoteBuilderService(Projects projects, Materials materials, Groups groups,
                                      QuoteRepository quotes, QuoteHistoryRepository quoteHistory,
                                      ProjectQuoteRepository projectQuotes, ClientRepository clients,
                                      TaxService taxService, TenantContext tenant) {
        this.projects = projects;
        this.materials = materials;
        this.groups = groups;
        this.quotes = quotes;
        this.quoteHistory = quoteHistory;
        this.projectQuotes = projectQuotes;
        this.clients = clients;
        this.taxService = taxService;
        this.tenant = tenant;
    }

    @Transactional
    public Long createFromMaterials(Long projectId, ProjectQuoteBuilderDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        Project project = projects.findByIdAndCompanyId(projectId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto " + projectId + " no encontrado"));

        // Todos los materiales disponibles del proyecto, por id, para validar
        // cada línea contra el pool sin ir a la base una vez por material.
        Map<Long, ProjectMaterial> pool = materials
                .findByCompanyIdAndProjectIdAndQuoteIdIsNullOrderByIdAsc(companyId, projectId)
                .stream().collect(Collectors.toMap(ProjectMaterial::getId, Function.identity()));

        Quote quote = new Quote();
        quote.setCompanyId(companyId);
        quote.setPartyType("client");
        quote.setDocNumber("COT-" + Instant.now().toEpochMilli());
        quote.setQuoteDate(req.quoteDate() != null ? req.quoteDate() : LocalDate.now());
        quote.setValidUntil(req.validUntil());
        quote.setCreatedBy(req.createdBy());
        quote.setNotes(req.notes());
        quote.setProfitCalcType("percent".equalsIgnoreCase(req.profitCalcType()) ? "percent" : "fixed");
        quote.setProfitValue(req.profitValue() != null ? req.profitValue() : BigDecimal.ZERO);
        quote.setStatus("borrador");
        quote.setProjectId(projectId);
        if (project.getClientId() != null) {
            // El cliente del proyecto es el de la cotización. Se copian también
            // los campos denormalizados (name/nit/email/contact) porque la
            // pantalla los lee de ahí, no de la relación.
            clients.findByIdAndCompanyId(project.getClientId(), companyId).ifPresent(c -> {
                quote.setClient(c);
                quote.setClientName(c.getName());
                quote.setClientNit(c.getNit());
                quote.setClientEmail(c.getEmail());
                quote.setClientContact(c.getPhone());
            });
        }

        // Valida y arma las líneas antes de tocar los materiales, para que un
        // material inválido no deje media cotización creada.
        BigDecimal total = BigDecimal.ZERO;
        for (ProjectQuoteBuilderDtos.LineRequest line : req.lines()) {
            List<ProjectMaterial> lineMaterials = line.materialIds().stream()
                    .map(id -> {
                        ProjectMaterial m = pool.get(id);
                        if (m == null) {
                            throw new IllegalStateException(
                                    "El material " + id + " no está disponible en este proyecto "
                                    + "(no existe o ya pertenece a otra cotización).");
                        }
                        return m;
                    })
                    .toList();

            String description = resolveDescription(companyId, line);
            BigDecimal sellPrice = line.sellPrice() != null
                    ? line.sellPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            QuoteItem item = new QuoteItem();
            item.setDescription(description);
            item.setSourceGroupId(line.sourceGroupId());
            item.setItemName(description);
            item.setUom(line.uom() != null ? line.uom() : "servicio");
            item.setQuantity(BigDecimal.ONE);
            item.setUnitPrice(sellPrice);
            item.setDiscount(BigDecimal.ZERO);
            item.setLineTotal(sellPrice);
            quote.addItem(item);
            total = total.add(sellPrice);
        }

        BigDecimal rate = taxService.rate();
        BigDecimal tax = total.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        quote.setTaxRate(rate);
        quote.setSubtotal(total);
        quote.setTax(tax);
        quote.setTotal(total.add(tax).setScale(2, RoundingMode.HALF_UP));

        Quote saved = quotes.save(quote);

        // Ahora que las líneas existen (con id), se estampa cada material con su
        // cotización y su línea: sale del pool y no puede ir a otra cotización.
        List<QuoteItem> savedItems = saved.getItems();
        int idx = 0;
        for (ProjectQuoteBuilderDtos.LineRequest line : req.lines()) {
            QuoteItem savedItem = savedItems.get(idx++);
            for (Long materialId : line.materialIds()) {
                ProjectMaterial m = pool.get(materialId);
                m.setQuoteId(saved.getId());
                m.setQuoteItemId(savedItem.getId());
                materials.update(m);
            }
        }

        // Enlace proyecto-cotización: no incluido hasta que se apruebe.
        ProjectQuote link = new ProjectQuote();
        link.setCompanyId(companyId);
        link.setProjectId(projectId);
        link.setQuoteId(saved.getId());
        link.setAmountSnapshot(saved.getTotal());
        link.setIncluded(Boolean.FALSE);
        link.setCreatedAt(Instant.now());
        projectQuotes.save(link);

        quoteHistory.save(new QuoteHistory(companyId, saved.getId(),
                "Cotización creada desde materiales del proyecto", req.createdBy()));
        return saved.getId();
    }

    /**
     * Libera los materiales de una cotización: vuelven al pool disponible
     * (quote_id = NULL). Se llama cuando la cotización se rechaza o cancela.
     */
    @Transactional
    public int releaseMaterials(Long quoteId) {
        Long companyId = tenant.getCompanyId();
        List<ProjectMaterial> assigned = materials.findByCompanyIdAndQuoteId(companyId, quoteId);
        for (ProjectMaterial m : assigned) {
            m.setQuoteId(null);
            m.setQuoteItemId(null);
            materials.update(m);
        }
        return assigned.size();
    }

    /** Default de descripción: nombre de la carpeta de origen, si se dio una. */
    private String resolveDescription(Long companyId, ProjectQuoteBuilderDtos.LineRequest line) {
        String desc = line.description() != null ? line.description().trim() : "";
        if (!desc.isBlank()) return desc;
        if (line.sourceGroupId() != null) {
            return groups.findByIdAndCompanyId(line.sourceGroupId(), companyId)
                    .map(ProjectMaterialGroup::getName)
                    .orElse("Línea de cotización");
        }
        return "Línea de cotización";
    }
}
