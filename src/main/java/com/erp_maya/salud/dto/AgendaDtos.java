package com.erp_maya.salud.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Agenda de Stackline Salud: profesionales, horarios, recursos, tipos de
 * cita, bloqueos y citas. Las horas viajan como instantes ISO-8601 con zona
 * (2026-11-02T09:00:00-06:00); los horarios semanales, como hora local de
 * Guatemala (09:00).
 *
 * Las vistas con listas llevan {@code JsonInclude(NON_NULL)}: Serde omite por
 * defecto las listas vacías y el front espera {@code []}.
 */
public final class AgendaDtos {

    private AgendaDtos() {}

    // ── Configuración ────────────────────────────────────────────────────

    /** Franja del horario semanal. `weekday` ISO: 1 = lunes … 7 = domingo. */
    @Serdeable
    public record ScheduleSlot(
            @NotNull Integer weekday,
            @NotNull LocalTime start,
            @NotNull LocalTime end,
            @Nullable Long resourceId) {}

    @Serdeable
    public record ScheduleRequest(@NotNull @Valid List<ScheduleSlot> slots) {}

    @Serdeable
    public record ProfessionalRequest(
            @Nullable @Size(max = 80) String specialty,
            @Nullable @Size(max = 40) String licenseNumber,
            @Nullable String color,
            @Nullable Boolean active) {}

    /**
     * Un usuario de la empresa y su perfil de salud. `configured` es false
     * para los usuarios que todavía no son profesionales: la pantalla los
     * ofrece para darlos de alta.
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProfessionalView(
            Long userId,
            String name,
            @Nullable String email,
            boolean configured,
            @Nullable String specialty,
            @Nullable String licenseNumber,
            @Nullable String color,
            boolean active,
            List<ScheduleSlot> schedule) {}

    @Serdeable
    public record ResourceRequest(
            @NotBlank @Size(max = 80) String name,
            @Nullable String kind,
            @Nullable Long branchId,
            @Nullable Boolean active) {}

    @Serdeable
    public record ResourceView(Long id, String name, String kind, @Nullable Long branchId, boolean active) {}

    @Serdeable
    public record TypeRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull Integer durationMinutes,
            @Nullable BigDecimal price,
            @Nullable String color,
            @Nullable Boolean requiresResource,
            @Nullable Boolean active) {}

    @Serdeable
    public record TypeView(Long id, String name, int durationMinutes, @Nullable BigDecimal price,
                           @Nullable String color, boolean requiresResource, boolean active) {}

    // ── Bloqueos ─────────────────────────────────────────────────────────

    @Serdeable
    public record BlockRequest(
            @Nullable Long professionalId,
            @Nullable Long resourceId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Nullable @Size(max = 200) String reason) {}

    @Serdeable
    public record BlockView(Long id, @Nullable Long professionalId, @Nullable Long resourceId,
                            Instant startsAt, Instant endsAt, @Nullable String reason) {}

    // ── Citas ────────────────────────────────────────────────────────────

    /**
     * Nueva cita. Sin `endsAt`, dura lo del tipo de cita. `status` puede ser
     * «confirmada» (por defecto: recepción la agenda con el paciente al
     * teléfono) o «solicitada» (queda pendiente de confirmar).
     */
    @Serdeable
    public record AppointmentRequest(
            @NotNull Long patientId,
            @NotNull Long professionalId,
            @NotNull Long typeId,
            @Nullable Long resourceId,
            @NotNull Instant startsAt,
            @Nullable Instant endsAt,
            @Nullable @Size(max = 300) String reason,
            @Nullable @Size(max = 1000) String notes,
            @Nullable String status,
            /** Agendar un control pendiente (fase 6): la cita queda ligada a su caso. */
            @Nullable Long followUpId,
            /** Sesión de un plan de tratamiento (fase 7): la cita queda en el plan y en su caso. */
            @Nullable Long planId) {}

    /**
     * Mover una cita. Sin profesional o recurso, se conservan los actuales.
     * `confirmed` (por defecto true): recepción la movió de acuerdo con el
     * paciente, así que sigue confirmada; en false vuelve a «solicitada».
     */
    @Serdeable
    public record RescheduleRequest(
            @NotNull Instant startsAt,
            @Nullable Instant endsAt,
            @Nullable Long professionalId,
            @Nullable Long resourceId,
            @Nullable Boolean confirmed) {}

    @Serdeable
    public record StatusRequest(@NotBlank String status, @Nullable @Size(max = 300) String reason) {}

    @Serdeable
    public record NotesRequest(@Nullable @Size(max = 300) String reason, @Nullable @Size(max = 1000) String notes) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppointmentView(
            Long id,
            Long patientId,
            String patientName,
            @Nullable String patientPhone,
            String recordNumber,
            Long professionalId,
            String professionalName,
            @Nullable String professionalColor,
            Long typeId,
            String typeName,
            @Nullable String typeColor,
            @Nullable Long resourceId,
            @Nullable String resourceName,
            Instant startsAt,
            Instant endsAt,
            String status,
            String source,
            @Nullable String reason,
            @Nullable String notes,
            @Nullable String cancelReason,
            Instant createdAt,
            @Nullable Instant confirmedAt,
            @Nullable Instant arrivedAt,
            @Nullable Instant startedAt,
            @Nullable Instant completedAt,
            @Nullable Instant cancelledAt,
            List<String> nextStatuses,
            @Nullable String reminderStatus,
            @Nullable Instant reminderSentAt,
            /** Caso clínico de la cita de control y la consulta que ya tiene, si tiene. */
            @Nullable Long caseId,
            @Nullable Long encounterId,
            @Nullable String encounterStatus,
            /** Fase 7: plan de tratamiento, cobro vigente (método y total) y precio del tipo. */
            @Nullable Long planId,
            @Nullable Long chargeId,
            @Nullable String chargeMethod,
            @Nullable BigDecimal chargeTotal,
            @Nullable BigDecimal price) {}

    // ── Ajustes ──────────────────────────────────────────────────────────

    /** Recordatorios por WhatsApp: si se mandan y cuántas horas antes de la cita (2 a 72). */
    @Serdeable
    public record SettingsView(boolean remindersEnabled, int reminderHours,
                               boolean controlRemindersEnabled, int controlReminderDays) {}

    @Serdeable
    public record SettingsRequest(@NotNull Boolean remindersEnabled, @NotNull Integer reminderHours,
                                  /** Fase 7; sin valor se conserva el actual. */
                                  @Nullable Boolean controlRemindersEnabled,
                                  @Nullable Integer controlReminderDays) {}

    // ── Disponibilidad ───────────────────────────────────────────────────

    @Serdeable
    public record Slot(Instant startsAt, Instant endsAt, @Nullable Long resourceId) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Availability(LocalDate date, Long professionalId, Long typeId, int durationMinutes, List<Slot> slots) {}
}
