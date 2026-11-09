package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Cita que toca recordar ahora (vista salud_v_reminders_due, solo lectura). */
@Entity
@Immutable
@Table(name = "salud_v_reminders_due")
public class ReminderCandidate {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    private String status;

    @Column(name = "reminder_attempts")
    private int reminderAttempts;

    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_phone")
    private String patientPhone;

    @Column(name = "professional_name")
    private String professionalName;

    @Column(name = "type_name")
    private String typeName;

    @Column(name = "resource_name")
    private String resourceName;

    @Column(name = "company_name")
    private String companyName;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getPatientId() { return patientId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getStatus() { return status; }
    public int getReminderAttempts() { return reminderAttempts; }
    public String getPatientName() { return patientName; }
    public String getPatientPhone() { return patientPhone; }
    public String getProfessionalName() { return professionalName; }
    public String getTypeName() { return typeName; }
    public String getResourceName() { return resourceName; }
    public String getCompanyName() { return companyName; }
}
