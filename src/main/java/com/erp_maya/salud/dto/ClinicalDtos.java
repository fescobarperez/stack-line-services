package com.erp_maya.salud.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Atención clínica de Stackline Salud (fase 6): la consulta con su nota SOAP,
 * signos vitales, diagnósticos y receta; el caso que une las consultas de un
 * mismo problema, y los controles pendientes.
 *
 * Las vistas con listas llevan {@code JsonInclude(NON_NULL)}: Serde omite por
 * defecto las listas vacías y el front espera {@code []}.
 */
public final class ClinicalDtos {

    private ClinicalDtos() {}

    // ── Entrada ──────────────────────────────────────────────────────────

    /** Signos vitales; cualquiera puede faltar. Presión en mmHg, temperatura en °C, peso en kg, talla en cm. */
    @Serdeable
    public record Vitals(
            @Nullable Integer bpSystolic,
            @Nullable Integer bpDiastolic,
            @Nullable Integer heartRate,
            @Nullable Integer respRate,
            @Nullable BigDecimal temperature,
            @Nullable Integer spo2,
            @Nullable BigDecimal weightKg,
            @Nullable BigDecimal heightCm,
            @Nullable Integer painScore) {}

    /** Diagnóstico: código CIE-10 (opcional) y descripción. Uno es el principal. */
    @Serdeable
    public record DiagnosisData(
            @Nullable @Size(max = 10) String code,
            @NotBlank @Size(max = 255) String description,
            @Nullable Boolean primary) {}

    @Serdeable
    public record PrescriptionData(
            @NotBlank @Size(max = 150) String medication,
            @Nullable @Size(max = 60) String dose,
            @Nullable @Size(max = 40) String route,
            @Nullable @Size(max = 60) String frequency,
            @Nullable Integer durationDays,
            @Nullable @Size(max = 40) String quantity,
            @Nullable @Size(max = 300) String instructions) {}

    /**
     * La consulta completa, como se guarda el borrador y como se firma.
     * Caso: `caseId` (uno abierto del paciente) o `newCaseTitle` (se crea al
     * firmar). Seguimiento: `followUpDays` crea el control al firmar.
     */
    @Serdeable
    public record EncounterRequest(
            @Nullable Long caseId,
            @Nullable @Size(max = 150) String newCaseTitle,
            @Nullable String subjective,
            @Nullable String objective,
            @Nullable String assessment,
            @Nullable String plan,
            @Nullable @Size(max = 2000) String instructions,
            @Nullable @Valid Vitals vitals,
            @Nullable String response,
            @Nullable String adherence,
            @Nullable @Size(max = 1000) String adverseEffects,
            @Nullable @Valid List<DiagnosisData> diagnoses,
            @Nullable @Valid List<PrescriptionData> prescriptions,
            @Nullable Integer followUpDays,
            @Nullable Long followUpTypeId,
            @Nullable @Size(max = 300) String followUpNotes) {}

    @Serdeable
    public record AddendumRequest(@NotBlank @Size(max = 2000) String text) {}

    @Serdeable
    public record CloseCaseRequest(@Nullable @Size(max = 1000) String outcome) {}

    // ── Salida ───────────────────────────────────────────────────────────

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VitalsView(
            @Nullable Integer bpSystolic,
            @Nullable Integer bpDiastolic,
            @Nullable Integer heartRate,
            @Nullable Integer respRate,
            @Nullable BigDecimal temperature,
            @Nullable Integer spo2,
            @Nullable BigDecimal weightKg,
            @Nullable BigDecimal heightCm,
            @Nullable Integer painScore,
            /** Índice de masa corporal, calculado con peso y talla. */
            @Nullable BigDecimal bmi) {}

    @Serdeable
    public record DiagnosisView(@Nullable String code, String description, boolean primary) {}

    @Serdeable
    public record PrescriptionView(String medication, @Nullable String dose, @Nullable String route,
                                   @Nullable String frequency, @Nullable Integer durationDays,
                                   @Nullable String quantity, @Nullable String instructions) {}

    @Serdeable
    public record AddendumView(Long id, @Nullable Long authorId, @Nullable String authorName, String text, Instant createdAt) {}

    /** Lo que se lista de una consulta: en el expediente y como «consulta anterior» del control. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EncounterSummary(
            Long id,
            @Nullable Long appointmentId,
            String status,
            Instant createdAt,
            @Nullable Instant signedAt,
            Long professionalId,
            String professionalName,
            @Nullable Long caseId,
            @Nullable String caseTitle,
            @Nullable String dxCode,
            @Nullable String dxDescription,
            @Nullable String response,
            long rxCount,
            VitalsView vitals) {}

    @Serdeable
    public record CaseOption(Long id, String title, Instant openedAt) {}

    /**
     * La consulta completa. `canEdit`: es borrador y quien la pide puede
     * editar. `previous`: la última firmada del mismo caso, para comparar en
     * el control. `openCases`: casos abiertos del paciente para elegir.
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EncounterView(
            Long id,
            Long patientId,
            String patientName,
            @Nullable String recordNumber,
            @Nullable Integer patientAge,
            @Nullable String patientSex,
            @Nullable Long appointmentId,
            @Nullable Instant appointmentStartsAt,
            @Nullable String appointmentReason,
            Long professionalId,
            String professionalName,
            @Nullable Long caseId,
            @Nullable String caseTitle,
            @Nullable String newCaseTitle,
            String status,
            @Nullable String subjective,
            @Nullable String objective,
            @Nullable String assessment,
            @Nullable String plan,
            @Nullable String instructions,
            VitalsView vitals,
            @Nullable String response,
            @Nullable String adherence,
            @Nullable String adverseEffects,
            List<DiagnosisView> diagnoses,
            List<PrescriptionView> prescriptions,
            List<AddendumView> addenda,
            @Nullable Integer followUpDays,
            @Nullable Long followUpTypeId,
            @Nullable String followUpNotes,
            Instant createdAt,
            @Nullable Instant signedAt,
            boolean canEdit,
            @Nullable EncounterSummary previous,
            List<CaseOption> openCases,
            /** Alergias del paciente («Penicilina (severa)»): se muestran al recetar. */
            List<String> allergies) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FollowUpView(
            Long id,
            Long caseId,
            @Nullable String caseTitle,
            LocalDate dueOn,
            @Nullable Long typeId,
            @Nullable Long professionalId,
            @Nullable String notes,
            String status,
            @Nullable Long appointmentId,
            /** Días para la fecha límite; negativo si ya venció. */
            long daysLeft,
            /** Fase 7: recordatorio por WhatsApp y lo que contestó el paciente. */
            @Nullable String reminderStatus,
            @Nullable Instant reminderSentAt,
            @Nullable String feeling,
            @Nullable Instant feelingAt,
            @Nullable String feelingNote) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseView(
            Long id,
            String title,
            String status,
            Instant openedAt,
            @Nullable Instant closedAt,
            @Nullable String outcome,
            List<EncounterSummary> encounters,
            List<FollowUpView> followUps) {}

    /** Historia clínica del paciente: casos con sus consultas y borradores sin caso todavía. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClinicalHistory(List<CaseView> cases, List<EncounterSummary> drafts) {}

    @Serdeable
    public record Cie10View(String code, String description) {}
}
