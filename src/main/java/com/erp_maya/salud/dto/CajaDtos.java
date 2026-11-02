package com.erp_maya.salud.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Caja y seguimiento de Stackline Salud (fase 7): cobro de citas, paquetes,
 * anticipos, planes de tratamiento, bandeja de controles y reportes.
 */
public final class CajaDtos {

    private CajaDtos() {}

    // ── Catálogo de paquetes ─────────────────────────────────────────────

    @Serdeable
    public record PackageRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull Long typeId,
            @NotNull Integer sessions,
            @NotNull BigDecimal price,
            /** Días de vigencia desde la compra; sin valor no vence. */
            @Nullable Integer validDays,
            @Nullable Boolean active) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PackageView(Long id, String name, Long typeId, @Nullable String typeName, int sessions,
                              BigDecimal price, BigDecimal sessionPrice, @Nullable Integer validDays, boolean active) {}

    // ── Cuenta del paciente ──────────────────────────────────────────────

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PatientPackageView(
            Long id,
            @Nullable Long packageId,
            String name,
            Long typeId,
            @Nullable String typeName,
            int sessionsTotal,
            int sessionsUsed,
            int sessionsLeft,
            BigDecimal price,
            @Nullable Long saleId,
            Instant purchasedAt,
            @Nullable LocalDate expiresOn,
            String status,
            /** Activo, con sesiones y sin vencer: se puede usar hoy. */
            boolean usable) {}

    /** Vender un paquete del catálogo: se factura en la caja abierta de quien lo vende. */
    @Serdeable
    public record SellPackageRequest(@NotNull Long packageId, @Nullable String paymentMethod) {}

    /** Recibir un anticipo: se factura al recibirlo y queda como saldo del paciente. */
    @Serdeable
    public record DepositRequest(@NotNull BigDecimal amount, @Nullable String paymentMethod,
                                 @Nullable @Size(max = 300) String notes) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreditView(Long id, String kind, BigDecimal amount, @Nullable Long saleId, @Nullable Long chargeId,
                             @Nullable String notes, Instant createdAt) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Account(Long patientId, BigDecimal creditBalance, List<PatientPackageView> packages,
                          List<ChargeView> charges, List<CreditView> credits) {}

    // ── Cobro de la cita ─────────────────────────────────────────────────

    /**
     * Cómo se cobra la cita. Con `patientPackageId` descuenta una sesión del
     * paquete; con `courtesy` no se cobra (pide el motivo en `notes`); si no,
     * se factura `price` (o el precio del tipo) menos `discount`, aplicando
     * primero el anticipo si `useCredit`.
     */
    @Serdeable
    public record ChargeRequest(
            @Nullable Long patientPackageId,
            @Nullable Boolean useCredit,
            @Nullable BigDecimal price,
            @Nullable BigDecimal discount,
            @Nullable String paymentMethod,
            @Nullable Boolean courtesy,
            @Nullable @Size(max = 300) String notes) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChargeView(
            Long id,
            Long appointmentId,
            Long patientId,
            Long typeId,
            @Nullable String typeName,
            @Nullable Instant appointmentStartsAt,
            String method,
            BigDecimal price,
            BigDecimal discount,
            BigDecimal total,
            BigDecimal creditAmount,
            BigDecimal cashAmount,
            BigDecimal serviceValue,
            @Nullable String paymentMethod,
            @Nullable Long saleId,
            @Nullable String saleNumber,
            @Nullable Long patientPackageId,
            @Nullable String packageName,
            String status,
            @Nullable String notes,
            Instant createdAt,
            @Nullable Instant voidedAt,
            @Nullable String voidReason) {}

    /** Lo que necesita el diálogo de cobro. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChargeOptions(
            Long appointmentId,
            String appointmentStatus,
            Long patientId,
            String patientName,
            Long typeId,
            String typeName,
            @Nullable BigDecimal price,
            BigDecimal creditBalance,
            /** Paquetes del paciente que sirven para esta cita, el sugerido primero. */
            List<PatientPackageView> packages,
            /** Quien cobra tiene un turno de caja abierto hoy. */
            boolean cashRegisterOpen,
            @Nullable ChargeView charge) {}

    @Serdeable
    public record VoidRequest(@NotBlank @Size(max = 300) String reason) {}

    /** El cobro anulado y, si tenía factura, el aviso de que esa se anula aparte. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VoidResult(ChargeView charge, @Nullable String warning) {}

    // ── Planes de tratamiento ────────────────────────────────────────────

    @Serdeable
    public record PlanRequest(
            @NotBlank @Size(max = 120) String title,
            @NotNull Long typeId,
            @Nullable Long professionalId,
            @NotNull Integer sessions,
            @Nullable Integer intervalDays,
            @Nullable @Size(max = 300) String notes) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanView(
            Long id,
            Long patientId,
            Long caseId,
            String title,
            Long typeId,
            @Nullable String typeName,
            @Nullable Long professionalId,
            @Nullable String professionalName,
            int sessions,
            /** Sesiones atendidas, agendadas (por venir) y las que faltan por agendar. */
            int done,
            int scheduled,
            int remaining,
            @Nullable Integer intervalDays,
            @Nullable String notes,
            String status,
            Instant createdAt,
            @Nullable Instant closedAt,
            /** Fecha sugerida para la siguiente sesión: la última más el intervalo. */
            @Nullable LocalDate nextDate) {}

    // ── Controles ────────────────────────────────────────────────────────

    /**
     * Un control de la bandeja. El caso, las notas y lo que contestó el
     * paciente son datos clínicos: solo llegan a quien tiene clinical_record.
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FollowUpItem(
            Long id,
            Long patientId,
            String patientName,
            @Nullable String patientPhone,
            @Nullable String recordNumber,
            @Nullable Long professionalId,
            @Nullable String professionalName,
            @Nullable Long typeId,
            @Nullable String typeName,
            LocalDate dueOn,
            long daysLeft,
            @Nullable String reminderStatus,
            @Nullable Instant reminderSentAt,
            /** El paciente ya contestó el recordatorio (sin decir qué, si no hay permiso clínico). */
            boolean answered,
            @Nullable Long caseId,
            @Nullable String caseTitle,
            @Nullable String notes,
            @Nullable String feeling,
            @Nullable Instant feelingAt,
            @Nullable String feelingNote) {}

    /** Lo que contestó el paciente al recordatorio del control. `phone` lo manda el asistente para comprobar que es él. */
    @Serdeable
    public record FeedbackRequest(@NotBlank String feeling, @Nullable String phone, @Nullable @Size(max = 500) String note) {}

    /** Para que el asistente ofrezca agendar el control con el profesional y el tipo correctos. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeedbackResult(Long id, Long patientId, String patientName, @Nullable Long professionalId, @Nullable String professionalName,
                                 @Nullable Long typeId, @Nullable String typeName, LocalDate dueOn, String status,
                                 String feeling) {}

    // ── Reportes ─────────────────────────────────────────────────────────

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Report(LocalDate from, LocalDate to, Totals totals, List<DayRow> byDay,
                         List<ProfessionalRow> byProfessional, List<ServiceRow> byService,
                         List<FollowUpItem> overdueFollowUps) {}

    /**
     * Totales del periodo. `noShowRate` = no asistió ÷ (atendidas + no
     * asistió), en %. `cashIn` es lo que entró en caja por Salud: cobros de
     * citas, paquetes vendidos y anticipos recibidos.
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Totals(int appointments, int completed, int noShow, int cancelled, @Nullable Double noShowRate,
                         int charges, BigDecimal serviceValue, BigDecimal chargesCash, BigDecimal creditApplied,
                         BigDecimal packagesSold, BigDecimal deposits, BigDecimal cashIn) {}

    @Serdeable
    public record DayRow(LocalDate date, int appointments, int completed, int noShow, int cancelled) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProfessionalRow(Long professionalId, String name, int appointments, int completed, int noShow,
                                  int cancelled, @Nullable Double noShowRate) {}

    @Serdeable
    public record ServiceRow(Long typeId, String name, int charges, int packageSessions, int courtesies,
                             BigDecimal serviceValue, BigDecimal cash, BigDecimal credit) {}
}
