package com.erp_maya.accounting.controller;

import com.erp_maya.accounting.dto.JournalTemplateDtos;
import com.erp_maya.accounting.service.JournalTemplateService;
import com.erp_maya.accounting.service.PostingRuleService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Plantillas de partidas y su asociación con casos de uso. Bajo /api/accounting,
 * así que exige el módulo de contabilidad (ModuleCatalog).
 */
@Controller("/api/accounting")
public class JournalTemplateController {

    private final JournalTemplateService templates;
    private final PostingRuleService rules;

    public JournalTemplateController(JournalTemplateService templates, PostingRuleService rules) {
        this.templates = templates;
        this.rules = rules;
    }

    @Get("/use-cases")
    public List<JournalTemplateDtos.UseCaseResponse> useCases() {
        return templates.useCases();
    }

    @Get("/journal-templates")
    public List<JournalTemplateDtos.TemplateResponse> list() {
        return templates.list();
    }

    @Get("/journal-templates/{id}")
    public JournalTemplateDtos.TemplateResponse get(Long id) {
        return templates.get(id);
    }

    @Post("/journal-templates")
    @Status(HttpStatus.CREATED)
    public JournalTemplateDtos.TemplateResponse create(@Valid @Body JournalTemplateDtos.TemplateRequest request) {
        return templates.create(request);
    }

    @Put("/journal-templates/{id}")
    public JournalTemplateDtos.TemplateResponse update(Long id, @Valid @Body JournalTemplateDtos.TemplateRequest request) {
        return templates.update(id, request);
    }

    @Delete("/journal-templates/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(Long id) {
        templates.delete(id);
    }

    @Post("/journal-templates/preview")
    public JournalTemplateDtos.PreviewResponse preview(@Valid @Body JournalTemplateDtos.PreviewRequest request) {
        return templates.preview(request);
    }

    @Get("/posting-rules")
    public List<JournalTemplateDtos.RuleResponse> rules() {
        return rules.list();
    }

    @Put("/posting-rules/{useCase}")
    public JournalTemplateDtos.RuleResponse putRule(String useCase, @Valid @Body JournalTemplateDtos.RuleRequest request) {
        return rules.put(useCase, request);
    }
}
