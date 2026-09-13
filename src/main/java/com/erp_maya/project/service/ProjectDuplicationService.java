package com.erp_maya.project.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.repository.ClientRepository;
import com.erp_maya.project.domain.Project;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.domain.ProjectMaterialGroup;
import com.erp_maya.project.domain.ProjectQuote;
import com.erp_maya.project.dto.ProjectDtos;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Groups;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.project.repository.ProjectQuoteRepository;
import com.erp_maya.project.repository.ProjectRepositories.Projects;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteCharge;
import com.erp_maya.quote.domain.QuoteHistory;
import com.erp_maya.quote.domain.QuoteItem;
import com.erp_maya.quote.domain.QuotePaymentTerm;
import com.erp_maya.quote.repository.QuoteChargeRepository;
import com.erp_maya.quote.repository.QuoteHistoryRepository;
import com.erp_maya.quote.repository.QuotePaymentTermRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import com.erp_maya.sequence.service.DocumentSequenceService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Duplica un proyecto completo para usarlo como plantilla.
 *
 * Vive aparte de ProjectService porque la operación cruza tres agregados
 * —proyecto, plan de materiales y (en la etapa 2) cotizaciones— y ninguno de
 * los servicios existentes es dueño natural de los tres.
 *
 * Lo único que el usuario captura es nombre y cliente; todo lo demás se hereda
 * del original. Lo que NO se copia es la ejecución: cargos (project_costs),
 * consumos de bodega y sus movimientos, ventas, facturas y pagos. Eso ocurrió
 * en el proyecto original y no tiene por qué aparecer en la copia.
 */
@Singleton
public class ProjectDuplicationService {

    private final Projects projects;
    private final Groups groups;
    private final Materials materials;
    private final ProjectQuoteRepository projectQuotes;
    private final QuoteRepository quotes;
    private final QuoteChargeRepository quoteCharges;
    private final QuotePaymentTermRepository quoteTerms;
    private final QuoteHistoryRepository quoteHistory;
    private final ClientRepository clients;
    private final DocumentSequenceService sequences;
    private final ProjectService projectService;
    private final TenantContext tenant;

    public ProjectDuplicationService(Projects projects, Groups groups, Materials materials,
                                     ProjectQuoteRepository projectQuotes, QuoteRepository quotes,
                                     QuoteChargeRepository quoteCharges, QuotePaymentTermRepository quoteTerms,
                                     QuoteHistoryRepository quoteHistory, ClientRepository clients,
                                     DocumentSequenceService sequences,
                                     ProjectService projectService, TenantContext tenant) {
        this.sequences = sequences;
        this.projects = projects;
        this.groups = groups;
        this.materials = materials;
        this.projectQuotes = projectQuotes;
        this.quotes = quotes;
        this.quoteCharges = quoteCharges;
        this.quoteTerms = quoteTerms;
        this.quoteHistory = quoteHistory;
        this.clients = clients;
        this.projectService = projectService;
        this.tenant = tenant;
    }

    /**
     * Copia el proyecto y su plan de materiales. Devuelve el id del nuevo.
     *
     * El alta del proyecto pasa por ProjectService.create para no reimplementar
     * el correlativo, la validación del cliente ni el estado inicial 'draft'.
     */
    @Transactional
    public Long duplicate(Long sourceId, ProjectDtos.DuplicateRequest req) {
        Long companyId = tenant.getCompanyId();
        Project source = projects.findByIdAndCompanyId(sourceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto " + sourceId + " no encontrado"));

        ProjectDtos.Response copia = projectService.create(new ProjectDtos.Request(
                req.name().trim(), req.clientId(), null,
                source.getCostCenterId(), source.getContractedAmount(), source.getCurrency(),
                source.getStartDate(), source.getEndDate(), source.getNotes(), null));

        Map<Long, Long> equivalenciaGrupos = copiarGrupos(companyId, sourceId, copia.id());
        Map<Long, ProjectMaterial> equivalenciaMateriales =
                copiarMateriales(companyId, sourceId, copia.id(), equivalenciaGrupos);
        copiarCotizaciones(companyId, source, copia.id(), req.clientId(),
                equivalenciaGrupos, equivalenciaMateriales);
        return copia.id();
    }

    /**
     * Copia el árbol de carpetas y devuelve el mapa grupoViejo → grupoNuevo.
     *
     * Recorre desde las raíces para que el padre exista siempre antes que el
     * hijo; el orden por sort_order del repositorio no lo garantiza.
     *
     * No reusa ProjectMaterialService.copyGroupTree a propósito: aquel copia
     * dentro del mismo proyecto, le agrega " (copia)" al nombre de la raíz y
     * arrastra las cantidades ejecutadas. Aquí el nombre se conserva y el mapa
     * es el entregable —la etapa 2 lo necesita para reestampar las cotizaciones.
     */
    private Map<Long, Long> copiarGrupos(Long companyId, Long sourceId, Long destinoId) {
        List<ProjectMaterialGroup> origen = groups.findByCompanyIdAndProjectIdOrderBySortOrderAsc(companyId, sourceId);
        Map<Long, List<ProjectMaterialGroup>> hijosPorPadre = origen.stream()
                .filter(grupo -> grupo.getParentGroupId() != null)
                .collect(Collectors.groupingBy(ProjectMaterialGroup::getParentGroupId));
        Map<Long, Long> equivalencia = new HashMap<>();
        origen.stream()
                .filter(grupo -> grupo.getParentGroupId() == null)
                .forEach(raiz -> copiarRama(raiz, null, hijosPorPadre, companyId, destinoId, equivalencia));
        return equivalencia;
    }

    private void copiarRama(ProjectMaterialGroup origen, Long padreNuevoId,
                            Map<Long, List<ProjectMaterialGroup>> hijosPorPadre,
                            Long companyId, Long destinoId, Map<Long, Long> equivalencia) {
        ProjectMaterialGroup copia = new ProjectMaterialGroup();
        copia.setCompanyId(companyId);
        copia.setProjectId(destinoId);
        copia.setParentGroupId(padreNuevoId);
        copia.setName(origen.getName());
        copia.setSortOrder(origen.getSortOrder());
        ProjectMaterialGroup guardada = groups.save(copia);
        equivalencia.put(origen.getId(), guardada.getId());

        for (ProjectMaterialGroup hijo : hijosPorPadre.getOrDefault(origen.getId(), List.of())) {
            copiarRama(hijo, guardada.getId(), hijosPorPadre, companyId, destinoId, equivalencia);
        }
    }

    /**
     * Copia los materiales conservando cantidad planificada, unidad, proveedor y
     * el costo estampado del original.
     *
     * Lo ejecutado sí se reinicia: quantity_ordered y quantity_consumed reflejan
     * salidas reales de bodega que en la copia no ocurrieron, y heredarlas haría
     * nacer el proyecto diciendo que ya gastó material que sigue en existencia.
     *
     * quote_id y quote_item_id quedan en NULL: todos los materiales vuelven al
     * pool disponible. La etapa 2 los reestampa contra las cotizaciones copiadas
     * —nunca contra las del original, que seguirían siendo dueñas de material en
     * dos proyectos a la vez.
     */
    private Map<Long, ProjectMaterial> copiarMateriales(Long companyId, Long sourceId, Long destinoId,
                                                        Map<Long, Long> equivalenciaGrupos) {
        Map<Long, ProjectMaterial> equivalencia = new HashMap<>();
        for (ProjectMaterial origen : materials.findByCompanyIdAndProjectIdOrderByIdAsc(companyId, sourceId)) {
            ProjectMaterial copia = new ProjectMaterial();
            copia.setCompanyId(companyId);
            copia.setProjectId(destinoId);
            // Un material sin carpeta cuelga de la raíz y así se queda.
            copia.setGroupId(origen.getGroupId() == null ? null : equivalenciaGrupos.get(origen.getGroupId()));
            copia.setProductId(origen.getProductId());
            copia.setSupplierId(origen.getSupplierId());
            copia.setQuantityPlanned(origen.getQuantityPlanned());
            copia.setUom(origen.getUom());
            copia.setUnitCostSnapshot(origen.getUnitCostSnapshot());
            copia.setCostSnapshotAt(origen.getCostSnapshotAt());
            copia.setQuantityOrdered(BigDecimal.ZERO);
            copia.setQuantityConsumed(BigDecimal.ZERO);
            copia.setStatus("planned");
            copia.setNotes(origen.getNotes());
            equivalencia.put(origen.getId(), materials.save(copia));
        }
        return equivalencia;
    }

    /**
     * Replica las cotizaciones del proyecto y vuelve a estampar los materiales
     * copiados contra ellas.
     *
     * Este es el nudo de la duplicación. Un material lleva quote_id y
     * quote_item_id: si la copia quedara con los del original, una cotización
     * del proyecto viejo sería dueña de material en el proyecto nuevo, el costo
     * se contaría dos veces y releaseMaterials() liberaría material ajeno. Por
     * eso las cotizaciones se copian aquí y no en un paso independiente: hacen
     * falta los mapas de grupos, materiales y líneas al mismo tiempo.
     *
     * Solo entran las cotizaciones enlazadas por ProjectQuote, que son las de
     * cliente. Las RFQ a proveedor no participan en proyectos.
     */
    private void copiarCotizaciones(Long companyId, Project origen, Long destinoId, Long clienteNuevoId,
                                    Map<Long, Long> equivalenciaGrupos,
                                    Map<Long, ProjectMaterial> equivalenciaMateriales) {
        List<ProjectQuote> enlaces = projectQuotes.findByCompanyIdAndProjectId(companyId, origen.getId());
        if (enlaces.isEmpty()) return;

        Client cliente = clients.findByIdAndCompanyId(clienteNuevoId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente " + clienteNuevoId + " no encontrado"));
        boolean mismoCliente = clienteNuevoId.equals(origen.getClientId());

        for (ProjectQuote enlace : enlaces) {
            Quote fuente = quotes.findByIdAndCompanyId(enlace.getQuoteId(), companyId).orElse(null);
            if (fuente == null) continue;

            Quote copia = new Quote();
            copia.setCompanyId(companyId);
            copia.setDocNumber(sequences.next(
                    "supplier".equalsIgnoreCase(fuente.getPartyType()) ? "RFQ" : "COT", "A"));
            copia.setPartyType(fuente.getPartyType());
            copia.setClient(cliente);
            copia.setClientName(cliente.getName());
            copia.setClientNit(cliente.getNit());
            copia.setClientEmail(cliente.getEmail());
            // El contacto no vive en Client: si el cliente cambió, el del
            // original es de otra empresa y arrastrarlo seria un dato falso.
            copia.setClientContact(mismoCliente ? fuente.getClientContact() : null);
            copia.setQuoteDate(fuente.getQuoteDate());
            copia.setValidUntil(fuente.getValidUntil());
            copia.setDeadline(fuente.getDeadline());
            copia.setLeadTime(fuente.getLeadTime());
            copia.setPaymentTerms(fuente.getPaymentTerms());
            copia.setNotes(fuente.getNotes());
            copia.setCreatedBy(fuente.getCreatedBy());
            copia.setSubtotal(fuente.getSubtotal());
            copia.setTax(fuente.getTax());
            copia.setTaxRate(fuente.getTaxRate());
            copia.setTotal(fuente.getTotal());
            copia.setProfitCalcType(fuente.getProfitCalcType());
            copia.setProfitValue(fuente.getProfitValue());
            copia.setProfitAmount(fuente.getProfitAmount());
            copia.setProjectId(destinoId);
            // Nadie aprobó la copia. Heredar 'aprobada' la haría contar como
            // costo firme en approvedQuoteStatus() sin respaldo comercial.
            copia.setStatus("borrador");

            List<QuoteItem> lineasFuente = new ArrayList<>(fuente.getItems());
            for (QuoteItem lineaFuente : lineasFuente) {
                QuoteItem linea = new QuoteItem();
                linea.setCompanyId(companyId);
                linea.setQuote(copia);
                linea.setProduct(lineaFuente.getProduct());
                linea.setQuantity(lineaFuente.getQuantity());
                linea.setItemName(lineaFuente.getItemName());
                linea.setUom(lineaFuente.getUom());
                linea.setDescription(lineaFuente.getDescription());
                // La línea recuerda de qué carpeta del proyecto salió.
                linea.setSourceGroupId(lineaFuente.getSourceGroupId() == null
                        ? null : equivalenciaGrupos.get(lineaFuente.getSourceGroupId()));
                linea.setUnitPrice(lineaFuente.getUnitPrice());
                linea.setDiscount(lineaFuente.getDiscount());
                linea.setLineTotal(lineaFuente.getLineTotal());
                copia.getItems().add(linea);
            }
            Quote guardada = quotes.save(copia);

            // Las líneas se guardan por cascada y conservan el orden en que se
            // agregaron, así que el índice empareja fuente con copia.
            Map<Long, Long> equivalenciaLineas = new HashMap<>();
            List<QuoteItem> lineasCopia = guardada.getItems();
            for (int i = 0; i < lineasFuente.size() && i < lineasCopia.size(); i++) {
                equivalenciaLineas.put(lineasFuente.get(i).getId(), lineasCopia.get(i).getId());
            }
            reestampar(companyId, fuente.getId(), guardada.getId(), equivalenciaLineas, equivalenciaMateriales);

            for (QuoteCharge cargoFuente : quoteCharges.findByCompanyIdAndQuoteIdOrderBySortOrderAsc(companyId, fuente.getId())) {
                QuoteCharge cargo = new QuoteCharge();
                cargo.setCompanyId(companyId);
                cargo.setQuoteId(guardada.getId());
                cargo.setCategory(cargoFuente.getCategory());
                cargo.setDescription(cargoFuente.getDescription());
                cargo.setCalcType(cargoFuente.getCalcType());
                cargo.setValue(cargoFuente.getValue());
                cargo.setComputedAmount(cargoFuente.getComputedAmount());
                cargo.setSortOrder(cargoFuente.getSortOrder());
                quoteCharges.save(cargo);
            }

            for (QuotePaymentTerm cuotaFuente : quoteTerms.findByCompanyIdAndQuoteIdOrderBySequenceAsc(companyId, fuente.getId())) {
                QuotePaymentTerm cuota = new QuotePaymentTerm();
                cuota.setCompanyId(companyId);
                cuota.setQuoteId(guardada.getId());
                cuota.setSequence(cuotaFuente.getSequence());
                cuota.setAmount(cuotaFuente.getAmount());
                cuota.setDueDate(cuotaFuente.getDueDate());
                cuota.setNotes(cuotaFuente.getNotes());
                quoteTerms.save(cuota);
            }

            ProjectQuote enlaceNuevo = new ProjectQuote();
            enlaceNuevo.setCompanyId(companyId);
            enlaceNuevo.setProjectId(destinoId);
            enlaceNuevo.setQuoteId(guardada.getId());
            enlaceNuevo.setAmountSnapshot(guardada.getTotal());
            // No incluida: la copia arranca en borrador y nadie la ha aprobado.
            enlaceNuevo.setIncluded(Boolean.FALSE);
            enlaceNuevo.setCreatedAt(Instant.now());
            projectQuotes.save(enlaceNuevo);

            // La bitácora del original no se copia: son hechos que le pasaron a
            // aquel documento. La copia estrena la suya.
            quoteHistory.save(new QuoteHistory(companyId, guardada.getId(),
                    "Cotización creada por duplicación de " + origen.getCode()
                            + " (" + fuente.getDocNumber() + ")", fuente.getCreatedBy()));
        }
    }

    /**
     * Estampa los materiales copiados con la cotización y la línea nuevas.
     *
     * Se recorre el original —el que sabe qué material iba en qué línea— y se
     * traduce cada id con los mapas.
     */
    private void reestampar(Long companyId, Long cotizacionFuenteId, Long cotizacionNuevaId,
                            Map<Long, Long> equivalenciaLineas,
                            Map<Long, ProjectMaterial> equivalenciaMateriales) {
        for (ProjectMaterial origen : materials.findByCompanyIdAndQuoteId(companyId, cotizacionFuenteId)) {
            ProjectMaterial copia = equivalenciaMateriales.get(origen.getId());
            if (copia == null) continue;
            copia.setQuoteId(cotizacionNuevaId);
            copia.setQuoteItemId(origen.getQuoteItemId() == null
                    ? null : equivalenciaLineas.get(origen.getQuoteItemId()));
            materials.update(copia);
        }
    }

}
