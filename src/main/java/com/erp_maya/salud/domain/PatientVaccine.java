package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Vacuna aplicada (dato clínico). */
@Entity
@Table(name = "salud_patient_vaccines")
public class PatientVaccine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String vaccine;

    private String dose;

    @Column(name = "applied_on")
    private LocalDate appliedOn;

    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getVaccine() { return vaccine; }
    public void setVaccine(String vaccine) { this.vaccine = vaccine; }

    public String getDose() { return dose; }
    public void setDose(String dose) { this.dose = dose; }

    public LocalDate getAppliedOn() { return appliedOn; }
    public void setAppliedOn(LocalDate appliedOn) { this.appliedOn = appliedOn; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
