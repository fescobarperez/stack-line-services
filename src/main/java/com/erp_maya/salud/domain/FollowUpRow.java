package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;

/** Control con los nombres de la bandeja (vista salud_v_follow_ups, solo lectura). */
@Entity
@Immutable
@Table(name = "salud_v_follow_ups")
public class FollowUpRow {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "professional_id")
    private Long professionalId;

    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "due_on")
    private LocalDate dueOn;

    private String notes;

    private String status;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "reminder_status")
    private String reminderStatus;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    private String feeling;

    @Column(name = "feeling_at")
    private Instant feelingAt;

    @Column(name = "feeling_note")
    private String feelingNote;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_phone")
    private String patientPhone;

    @Column(name = "record_number")
    private String recordNumber;

    @Column(name = "professional_name")
    private String professionalName;

    @Column(name = "type_name")
    private String typeName;

    @Column(name = "case_title")
    private String caseTitle;

    @Column(name = "case_status")
    private String caseStatus;

    public Long getId() { return id; }

    public Long getCompanyId() { return companyId; }

    public Long getPatientId() { return patientId; }

    public Long getCaseId() { return caseId; }

    public Long getProfessionalId() { return professionalId; }

    public Long getTypeId() { return typeId; }

    public LocalDate getDueOn() { return dueOn; }

    public String getNotes() { return notes; }

    public String getStatus() { return status; }

    public Long getAppointmentId() { return appointmentId; }

    public String getReminderStatus() { return reminderStatus; }

    public Instant getReminderSentAt() { return reminderSentAt; }

    public String getFeeling() { return feeling; }

    public Instant getFeelingAt() { return feelingAt; }

    public String getFeelingNote() { return feelingNote; }

    public Instant getCreatedAt() { return createdAt; }

    public String getPatientName() { return patientName; }

    public String getPatientPhone() { return patientPhone; }

    public String getRecordNumber() { return recordNumber; }

    public String getProfessionalName() { return professionalName; }

    public String getTypeName() { return typeName; }

    public String getCaseTitle() { return caseTitle; }

    public String getCaseStatus() { return caseStatus; }
}
