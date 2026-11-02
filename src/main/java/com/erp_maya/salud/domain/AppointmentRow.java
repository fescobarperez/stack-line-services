package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Cita con los nombres de paciente, profesional, tipo y recurso (vista, solo lectura). */
@Entity
@Immutable
@Table(name = "salud_v_appointments")
public class AppointmentRow {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "professional_id")
    private Long professionalId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    private String status;

    private String source;

    private String reason;

    private String notes;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at")
    private Instant createdAt;

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

    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_phone")
    private String patientPhone;

    @Column(name = "record_number")
    private String recordNumber;

    @Column(name = "professional_name")
    private String professionalName;

    @Column(name = "professional_color")
    private String professionalColor;

    @Column(name = "type_name")
    private String typeName;

    @Column(name = "type_color")
    private String typeColor;

    @Column(name = "resource_name")
    private String resourceName;

    @Column(name = "reminder_status")
    private String reminderStatus;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "encounter_status")
    private String encounterStatus;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "charge_id")
    private Long chargeId;

    @Column(name = "charge_method")
    private String chargeMethod;

    @Column(name = "charge_total")
    private java.math.BigDecimal chargeTotal;

    @Column(name = "type_price")
    private java.math.BigDecimal typePrice;

    public Long getId() { return id; }

    public Long getCompanyId() { return companyId; }

    public Long getPatientId() { return patientId; }

    public Long getProfessionalId() { return professionalId; }

    public Long getResourceId() { return resourceId; }

    public Long getTypeId() { return typeId; }

    public Instant getStartsAt() { return startsAt; }

    public Instant getEndsAt() { return endsAt; }

    public String getStatus() { return status; }

    public String getSource() { return source; }

    public String getReason() { return reason; }

    public String getNotes() { return notes; }

    public String getCancelReason() { return cancelReason; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getConfirmedAt() { return confirmedAt; }

    public Instant getArrivedAt() { return arrivedAt; }

    public Instant getStartedAt() { return startedAt; }

    public Instant getCompletedAt() { return completedAt; }

    public Instant getCancelledAt() { return cancelledAt; }

    public String getPatientName() { return patientName; }

    public String getPatientPhone() { return patientPhone; }

    public String getRecordNumber() { return recordNumber; }

    public String getProfessionalName() { return professionalName; }

    public String getProfessionalColor() { return professionalColor; }

    public String getTypeName() { return typeName; }

    public String getTypeColor() { return typeColor; }

    public String getResourceName() { return resourceName; }

    public String getReminderStatus() { return reminderStatus; }

    public Instant getReminderSentAt() { return reminderSentAt; }

    public Long getCaseId() { return caseId; }
    public Long getEncounterId() { return encounterId; }
    public String getEncounterStatus() { return encounterStatus; }
    public Long getPlanId() { return planId; }
    public Long getChargeId() { return chargeId; }
    public String getChargeMethod() { return chargeMethod; }
    public java.math.BigDecimal getChargeTotal() { return chargeTotal; }
    public java.math.BigDecimal getTypePrice() { return typePrice; }
}
