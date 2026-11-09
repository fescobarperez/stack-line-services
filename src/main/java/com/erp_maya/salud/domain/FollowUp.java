package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Control pendiente de un caso: se agenda como cita y se cumple al atenderla. */
@Entity
@Table(name = "salud_follow_ups")
public class FollowUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "professional_id")
    private Long professionalId;

    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    private String notes;

    @Column(nullable = false)
    private String status = "pendiente";

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    // Recordatorio por WhatsApp antes de la fecha límite (fase 7).
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

    /** Cómo dice el paciente que se siente: mejor, igual o peor. */
    private String feeling;

    @Column(name = "feeling_at")
    private Instant feelingAt;

    @Column(name = "feeling_note")
    private String feelingNote;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }

    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }

    public Long getProfessionalId() { return professionalId; }
    public void setProfessionalId(Long professionalId) { this.professionalId = professionalId; }

    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }

    public LocalDate getDueOn() { return dueOn; }
    public void setDueOn(LocalDate dueOn) { this.dueOn = dueOn; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

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

    public String getFeeling() { return feeling; }
    public void setFeeling(String feeling) { this.feeling = feeling; }

    public Instant getFeelingAt() { return feelingAt; }
    public void setFeelingAt(Instant feelingAt) { this.feelingAt = feelingAt; }

    public String getFeelingNote() { return feelingNote; }
    public void setFeelingNote(String feelingNote) { this.feelingNote = feelingNote; }
}
