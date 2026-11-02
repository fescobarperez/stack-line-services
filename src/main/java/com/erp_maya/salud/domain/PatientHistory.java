package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Antecedente del paciente: crónico, quirúrgico, familiar, etc. (dato clínico). */
@Entity
@Table(name = "salud_patient_history")
public class PatientHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String description;

    @Column(name = "cie10_code")
    private String cie10Code;

    private String relative;

    private String occurred;

    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCie10Code() { return cie10Code; }
    public void setCie10Code(String cie10Code) { this.cie10Code = cie10Code; }

    public String getRelative() { return relative; }
    public void setRelative(String relative) { this.relative = relative; }

    public String getOccurred() { return occurred; }
    public void setOccurred(String occurred) { this.occurred = occurred; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
