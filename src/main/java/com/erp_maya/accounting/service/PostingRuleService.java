package com.erp_maya.accounting.service;

import com.erp_maya.accounting.domain.JournalTemplate;
import com.erp_maya.accounting.domain.PostingRule;
import com.erp_maya.accounting.dto.JournalTemplateDtos;
import com.erp_maya.accounting.repository.JournalTemplateRepository;
import com.erp_maya.accounting.repository.PostingRuleRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asociación de plantilla con caso de uso: qué partida se ejecuta sola en cada
 * punto del sistema (Contabilidad → Partidas automáticas → Asociación con casos de uso).
 */
@Singleton
public class PostingRuleService {

    private final PostingRuleRepository rules;
    private final JournalTemplateRepository templates;
    private final TenantContext tenant;

    public PostingRuleService(PostingRuleRepository rules, JournalTemplateRepository templates,
                              TenantContext tenant) {
        this.rules = rules;
        this.templates = templates;
        this.tenant = tenant;
    }

    /** Todos los casos de uso, con su plantilla o sin ninguna, en el orden del catálogo. */
    @Transactional
    public List<JournalTemplateDtos.RuleResponse> list() {
        Map<String, PostingRule> porCaso = rules.findByCompanyId(tenant.getCompanyId()).stream()
                .collect(Collectors.toMap(PostingRule::getUseCase, Function.identity(), (a, b) -> a));
        return Arrays.stream(PostingCase.values()).map(c -> toResponse(c, porCaso.get(c.code()))).toList();
    }

    /** Asocia (o cambia) la plantilla de un caso de uso. */
    @Transactional
    public JournalTemplateDtos.RuleResponse put(String useCaseCode, JournalTemplateDtos.RuleRequest req) {
        Long companyId = tenant.getCompanyId();
        PostingCase useCase = PostingCase.byCode(useCaseCode).orElseThrow(() ->
                new ResourceNotFoundException("Caso de uso «" + useCaseCode + "» no encontrado"));
        JournalTemplate tpl = templates.findByIdAndCompanyId(req.templateId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla " + req.templateId() + " no encontrada"));
        if (!useCase.code().equals(tpl.getUseCase())) {
            throw new IllegalStateException("La plantilla «" + tpl.getName() + "» es para otro caso de uso; "
                    + "elige una hecha para «" + useCase.label() + "».");
        }
        if (!Boolean.TRUE.equals(tpl.getActive())) {
            throw new IllegalStateException("La plantilla «" + tpl.getName() + "» está desactivada.");
        }

        PostingRule rule = rules.findByCompanyIdAndUseCase(companyId, useCase.code()).orElseGet(() -> {
            PostingRule r = new PostingRule();
            r.setCompanyId(companyId);
            r.setUseCase(useCase.code());
            return r;
        });
        rule.setTemplate(tpl);
        rule.setActive(!Boolean.FALSE.equals(req.active()));
        rule = rule.getId() == null ? rules.save(rule) : rules.update(rule);
        return toResponse(useCase, rule);
    }

    private static JournalTemplateDtos.RuleResponse toResponse(PostingCase c, PostingRule r) {
        return new JournalTemplateDtos.RuleResponse(c.code(), c.label(), c.group(),
                r != null ? r.getTemplate().getId() : null,
                r != null ? r.getTemplate().getName() : null,
                r != null ? r.getActive() : null);
    }
}
