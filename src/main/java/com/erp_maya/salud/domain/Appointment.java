package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Cita. El no solape lo garantiza la base (EXCLUDE en 090_salud_agenda.sql). */
@Entity
@Table(name = "salud_appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "professional_id", nullable = false)
    private Long professionalId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String source;

    private String reason;

    private String notes;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "status_changed_by")
    private Long statusChangedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "reminder_status")
    private String reminderStatus;

    @Column(name = "reminder_attempts", nullable = false)
    private int reminderAttempts;

    @Column(name = "reminder_next_at")
    private Instant reminderNextAt;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "reminder_error")
    private String reminderError;

    /** Caso clínico al que pertenece la cita de control (092). */
    @Column(name = "case_id")
    private Long caseId;

    /** Plan de tratamiento al que pertenece la cita (fase 7). */
    @Column(name = "plan_id")
    private Long planId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getProfessionalId() { return professionalId; }
    public void setProfessionalId(Long professionalId) { this.professionalId = professionalId; }

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getStatusChangedBy() { return statusChangedBy; }
    public void setStatusChangedBy(Long statusChangedBy) { this.statusChangedBy = statusChangedBy; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(Instant arrivedAt) { this.arrivedAt = arrivedAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getReminderStatus() { return reminderStatus; }
    public void setReminderStatus(String reminderStatus) { this.reminderStatus = reminderStatus; }

    public int getReminderAttempts() { return reminderAttempts; }
    public void setReminderAttempts(int reminderAttempts) { this.reminderAttempts = reminderAttempts; }

    public Instant getReminderNextAt() { return reminderNextAt; }
    public void setReminderNextAt(Instant reminderNextAt) { this.reminderNextAt = reminderNextAt; }

    public Instant getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Instant reminderSentAt) { this.reminderSentAt = reminderSentAt; }

    public String getReminderError() { return reminderError; }
    public void setReminderError(String reminderError) { this.reminderError = reminderError; }

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
}
