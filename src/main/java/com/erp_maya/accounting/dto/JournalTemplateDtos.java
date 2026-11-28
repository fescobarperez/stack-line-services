package com.erp_maya.accounting.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Plantillas de partidas, casos de uso y su asociación (Contabilidad → Partidas automáticas). */
public final class JournalTemplateDtos {

    private JournalTemplateDtos() {}

    @Serdeable
    public record AmountInfo(String key, String label) {}

    /** Un caso de uso del catálogo: qué montos ofrece y si trae cuenta del documento. */
    @Serdeable
    public record UseCaseResponse(String code, String name, String group, List<AmountInfo> amounts,
                                  String documentAccount, Map<String, BigDecimal> example) {}

    @Serdeable
    public record LineRequest(@NotBlank String side, Long accountId, Boolean usesDocumentAccount,
                              @NotEmpty List<String> amounts, @Size(max = 200) String description,
                              Boolean useCostCenter) {}

    @Serdeable
    public record TemplateRequest(@NotBlank String useCase, @NotBlank @Size(max = 120) String name,
                                  @Size(max = 500) String description, Boolean active,
                                  @NotEmpty List<@Valid LineRequest> lines) {}

    @Serdeable
    public record LineResponse(Long id, Integer position, String side, Long accountId, String accountCode,
                               String accountName, Boolean usesDocumentAccount, List<String> amounts,
                               String description, Boolean useCostCenter) {}

    /** `associatedTo`: el caso de uso donde está asociada, o null. */
    @Serdeable
    public record TemplateResponse(Long id, String useCase, String useCaseName, String name, String description,
                                   Boolean active, Integer version, String associatedTo, boolean used,
                                   List<LineResponse> lines) {}

    /**
     * Probar una plantilla (guardada o no) con montos de ejemplo o con los que
     * se manden. Sin @Valid en la plantilla a propósito: el editor la prueba
     * mientras se arma, incompleta, y los faltantes vuelven como errores.
     */
    @Serdeable
    public record PreviewRequest(@NotNull TemplateRequest template, Map<String, BigDecimal> amounts) {}

    @Serdeable
    public record PreviewLine(String side, String accountCode, String accountName, boolean documentAccount,
                              BigDecimal debit, BigDecimal credit, String description) {}

    @Serdeable
    public record PreviewResponse(List<PreviewLine> lines, BigDecimal totalDebit, BigDecimal totalCredit,
                                  boolean balanced, List<String> errors, Map<String, BigDecimal> amounts) {}

    /** Un caso de uso con su plantilla asociada (o sin ninguna). */
    @Serdeable
    public record RuleResponse(String useCase, String useCaseName, String group, Long templateId,
                               String templateName, Boolean active) {}

    @Serdeable
    public record RuleRequest(@NotNull Long templateId, Boolean active) {}
}
