package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Responsable del paciente: tutor legal y/o quien paga. Siempre es otro cliente. */
@Entity
@Table(name = "salud_patient_guardians")
public class PatientGuardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private String relationship;

    @Column(name = "is_legal_guardian", nullable = false)
    private boolean legalGuardian;

    @Column(name = "is_payer", nullable = false)
    private boolean payer;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public boolean isLegalGuardian() { return legalGuardian; }
    public void setLegalGuardian(boolean legalGuardian) { this.legalGuardian = legalGuardian; }

    public boolean isPayer() { return payer; }
    public void setPayer(boolean payer) { this.payer = payer; }
}
