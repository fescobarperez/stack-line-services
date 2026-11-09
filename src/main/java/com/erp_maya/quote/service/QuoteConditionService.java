package com.erp_maya.quote.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.quote.domain.Quote;
import com.erp_maya.quote.domain.QuoteCondition;
import com.erp_maya.quote.domain.QuoteConditionValue;
import com.erp_maya.quote.dto.QuoteConditionDtos;
import com.erp_maya.quote.repository.QuoteConditionRepository;
import com.erp_maya.quote.repository.QuoteConditionValueRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Condiciones comerciales de la empresa: se imprimen, en orden, en cada
 * cotización. Las marcadas «per_quote» las define el asesor en cada una: la
 * cotización nueva copia el valor por defecto y él lo cambia en «Editar
 * borrador»; el PDF imprime el de la cotización.
 */
@Singleton
public class QuoteConditionService {

    private final QuoteConditionRepository repository;
    private final QuoteConditionValueRepository values;
    private final QuoteRepository quotes;
    private final TenantContext tenant;

    public QuoteConditionService(QuoteConditionRepository repository, QuoteConditionValueRepository values,
                                 QuoteRepository quotes, TenantContext tenant) {
        this.repository = repository;
        this.values = values;
        this.quotes = quotes;
        this.tenant = tenant;
    }

    @Transactional
    public List<QuoteConditionDtos.Response> list() {
        return repository.listarPorEmpresa(tenant.getCompanyId())
                .stream().map(QuoteConditionService::toResponse).toList();
    }

    /** Las que salen en el PDF. */
    @Transactional
    public List<QuoteCondition> activas(Long companyId) {
        return repository.listarActivas(companyId);
    }

    /**
     * Las condiciones activas como salen en el PDF de esta cotización: las
     * que define el asesor, con su valor (o el de por defecto si la condición
     * se creó después de la cotización). Copias sueltas: no se persisten.
     */
    @Transactional
    public List<QuoteCondition> paraCotizacion(Long companyId, Long quoteId) {
        Map<Long, String> propios = valoresDe(companyId, quoteId);
        return repository.listarActivas(companyId).stream().map(c -> {
            QuoteCondition copia = new QuoteCondition();
            copia.setId(c.getId());
            copia.setCompanyId(c.getCompanyId());
            copia.setTitle(c.getTitle());
            copia.setBody(Boolean.TRUE.equals(c.getPerQuote()) ? propios.getOrDefault(c.getId(), c.getBody()) : c.getBody());
            copia.setSortOrder(c.getSortOrder());
            copia.setActive(c.getActive());
            copia.setPerQuote(c.getPerQuote());
            return copia;
        }).toList();
    }

    /** Copia los valores por defecto de las condiciones del asesor a una cotización nueva. */
    @Transactional
    public void copiarACotizacion(Long companyId, Long quoteId) {
        for (QuoteCondition c : repository.listarActivas(companyId)) {
            if (!Boolean.TRUE.equals(c.getPerQuote())) continue;
            QuoteConditionValue v = new QuoteConditionValue();
            v.setCompanyId(companyId);
            v.setQuoteId(quoteId);
            v.setConditionId(c.getId());
            v.setValue(c.getBody());
            values.save(v);
        }
    }

    /** Las condiciones que define el asesor en esta cotización, con su valor. */
    @Transactional
    public List<QuoteConditionDtos.QuoteValue> deCotizacion(Long quoteId) {
        Long companyId = tenant.getCompanyId();
        cotizacion(quoteId, companyId);
        Map<Long, String> propios = valoresDe(companyId, quoteId);
        return repository.listarActivas(companyId).stream()
                .filter(c -> Boolean.TRUE.equals(c.getPerQuote()))
                .map(c -> new QuoteConditionDtos.QuoteValue(c.getId(), c.getTitle(),
                        propios.getOrDefault(c.getId(), c.getBody()), c.getBody()))
                .toList();
    }

    /** Guarda los valores del asesor (solo mientras la cotización se puede editar). */
    @Transactional
    public List<QuoteConditionDtos.QuoteValue> guardarDeCotizacion(Long quoteId, QuoteConditionDtos.QuoteValuesRequest req) {
        Long companyId = tenant.getCompanyId();
        Quote q = cotizacion(quoteId, companyId);
        if (!QuoteService.esEditable(q.getStatus())) {
            throw new IllegalStateException("Solo se pueden editar cotizaciones en borrador o prospecto");
        }
        Map<Long, QuoteCondition> delAsesor = repository.listarPorEmpresa(companyId).stream()
                .filter(c -> Boolean.TRUE.equals(c.getPerQuote()))
                .collect(Collectors.toMap(QuoteCondition::getId, Function.identity()));
        Map<Long, QuoteConditionValue> existentes = values.findByCompanyIdAndQuoteId(companyId, quoteId).stream()
                .collect(Collectors.toMap(QuoteConditionValue::getConditionId, Function.identity(), (a, b) -> a));
        for (QuoteConditionDtos.QuoteValueInput in : req.values()) {
            if (!delAsesor.containsKey(in.conditionId())) {
                throw new IllegalStateException("La condición " + in.conditionId() + " no la define el asesor");
            }
            QuoteConditionValue v = existentes.get(in.conditionId());
            if (v == null) {
                v = new QuoteConditionValue();
                v.setCompanyId(companyId);
                v.setQuoteId(quoteId);
                v.setConditionId(in.conditionId());
            }
            v.setValue(in.value().trim());
            if (v.getId() == null) values.save(v); else values.update(v);
        }
        return deCotizacion(quoteId);
    }

    private Map<Long, String> valoresDe(Long companyId, Long quoteId) {
        Map<Long, String> out = new HashMap<>();
        values.findByCompanyIdAndQuoteId(companyId, quoteId).forEach(v -> out.put(v.getConditionId(), v.getValue()));
        return out;
    }

    private Quote cotizacion(Long quoteId, Long companyId) {
        return quotes.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización " + quoteId + " no encontrada"));
    }

    @Transactional
    public QuoteConditionDtos.Response create(QuoteConditionDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        QuoteCondition c = new QuoteCondition();
        c.setCompanyId(companyId);
        apply(c, req);
        if (req.sortOrder() == null) {
            // Al final de la lista.
            c.setSortOrder(repository.listarPorEmpresa(companyId).stream()
                    .mapToInt(QuoteCondition::getSortOrder).max().orElse(0) + 1);
        }
        return toResponse(repository.save(c));
    }

    @Transactional
    public QuoteConditionDtos.Response update(Long id, QuoteConditionDtos.Request req) {
        QuoteCondition c = find(id);
        apply(c, req);
        return toResponse(repository.update(c));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private void apply(QuoteCondition c, QuoteConditionDtos.Request req) {
        c.setTitle(req.title().trim());
        c.setBody(req.body().trim());
        if (req.sortOrder() != null) c.setSortOrder(req.sortOrder());
        c.setActive(req.active() == null || req.active());
        if (req.perQuote() != null) c.setPerQuote(req.perQuote());
    }

    private QuoteCondition find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Condición " + id + " no encontrada"));
    }

    private static QuoteConditionDtos.Response toResponse(QuoteCondition c) {
        return new QuoteConditionDtos.Response(c.getId(), c.getTitle(), c.getBody(),
                c.getSortOrder() == null ? 0 : c.getSortOrder(), !Boolean.FALSE.equals(c.getActive()),
                Boolean.TRUE.equals(c.getPerQuote()));
    }
}
