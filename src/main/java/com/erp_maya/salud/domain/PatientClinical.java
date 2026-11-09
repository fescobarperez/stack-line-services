package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Datos clínicos base del paciente. Solo con permiso de expediente clínico. */
@Entity
@Table(name = "salud_patient_clinical")
public class PatientClinical {

    @Id
    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "blood_type")
    private String bloodType;

    private String religion;

    private String disability;

    @Column(name = "special_needs")
    private String specialNeeds;

    private String smoking;

    private String alcohol;

    @Column(name = "physical_activity")
    private String physicalActivity;

    private String diet;

    @Column(name = "gyneco_obstetric")
    private String gynecoObstetric;

    private String alerts;

    @Column(name = "advance_directives")
    private String advanceDirectives;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getReligion() { return religion; }
    public void setReligion(String religion) { this.religion = religion; }

    public String getDisability() { return disability; }
    public void setDisability(String disability) { this.disability = disability; }

    public String getSpecialNeeds() { return specialNeeds; }
    public void setSpecialNeeds(String specialNeeds) { this.specialNeeds = specialNeeds; }

    public String getSmoking() { return smoking; }
    public void setSmoking(String smoking) { this.smoking = smoking; }

    public String getAlcohol() { return alcohol; }
    public void setAlcohol(String alcohol) { this.alcohol = alcohol; }

    public String getPhysicalActivity() { return physicalActivity; }
    public void setPhysicalActivity(String physicalActivity) { this.physicalActivity = physicalActivity; }

    public String getDiet() { return diet; }
    public void setDiet(String diet) { this.diet = diet; }

    public String getGynecoObstetric() { return gynecoObstetric; }
    public void setGynecoObstetric(String gynecoObstetric) { this.gynecoObstetric = gynecoObstetric; }

    public String getAlerts() { return alerts; }
    public void setAlerts(String alerts) { this.alerts = alerts; }

    public String getAdvanceDirectives() { return advanceDirectives; }
    public void setAdvanceDirectives(String advanceDirectives) { this.advanceDirectives = advanceDirectives; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
