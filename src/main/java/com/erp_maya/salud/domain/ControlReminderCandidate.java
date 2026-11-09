package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

/** Control que toca recordar ahora (vista salud_v_control_reminders_due, solo lectura). */
@Entity
@Immutable
@Table(name = "salud_v_control_reminders_due")
public class ControlReminderCandidate {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "reminder_attempts")
    private int reminderAttempts;

    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "professional_id")
    private Long professionalId;

    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_phone")
    private String patientPhone;

    @Column(name = "professional_name")
    private String professionalName;

    @Column(name = "type_name")
    private String typeName;

    @Column(name = "company_name")
    private String companyName;

    public Long getId() { return id; }

    public Long getCompanyId() { return companyId; }

    public Long getPatientId() { return patientId; }

    public LocalDate getDueOn() { return dueOn; }

    public int getReminderAttempts() { return reminderAttempts; }

    public Long getTypeId() { return typeId; }

    public Long getProfessionalId() { return professionalId; }

    public String getPatientName() { return patientName; }

    public String getPatientPhone() { return patientPhone; }

    public String getProfessionalName() { return professionalName; }

    public String getTypeName() { return typeName; }

    public String getCompanyName() { return companyName; }
}
