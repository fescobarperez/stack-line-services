package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/** Consulta con profesional, caso y diagnóstico principal (vista salud_v_encounters). */
@Entity
@Immutable
@Table(name = "salud_v_encounters")
public class EncounterRow {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "professional_id")
    private Long professionalId;

    @Column(name = "case_id")
    private Long caseId;

    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    private String response;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "heart_rate")
    private Integer heartRate;

    private BigDecimal temperature;

    private Integer spo2;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(name = "professional_name")
    private String professionalName;

    @Column(name = "case_title")
    private String caseTitle;

    @Column(name = "dx_code")
    private String dxCode;

    @Column(name = "dx_description")
    private String dxDescription;

    @Column(name = "rx_count")
    private Long rxCount;

    public Long getId() { return id; }

    public Long getCompanyId() { return companyId; }

    public Long getPatientId() { return patientId; }

    public Long getAppointmentId() { return appointmentId; }

    public Long getProfessionalId() { return professionalId; }

    public Long getCaseId() { return caseId; }

    public String getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getSignedAt() { return signedAt; }

    public String getResponse() { return response; }

    public Integer getPainScore() { return painScore; }

    public Integer getBpSystolic() { return bpSystolic; }

    public Integer getBpDiastolic() { return bpDiastolic; }

    public Integer getHeartRate() { return heartRate; }

    public BigDecimal getTemperature() { return temperature; }

    public Integer getSpo2() { return spo2; }

    public BigDecimal getWeightKg() { return weightKg; }

    public BigDecimal getHeightCm() { return heightCm; }

    public String getProfessionalName() { return professionalName; }

    public String getCaseTitle() { return caseTitle; }

    public String getDxCode() { return dxCode; }

    public String getDxDescription() { return dxDescription; }

    public Long getRxCount() { return rxCount; }
}
