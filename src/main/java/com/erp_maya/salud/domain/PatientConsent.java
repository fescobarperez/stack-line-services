package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Consentimiento firmado. Solo se agrega o se revoca; nunca se edita. */
@Entity
@Table(name = "salud_patient_consents")
public class PatientConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String kind;

    private String version;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    private String notes;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }

    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getRecordedBy() { return recordedBy; }
    public void setRecordedBy(Long recordedBy) { this.recordedBy = recordedBy; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public Long getRevokedBy() { return revokedBy; }
    public void setRevokedBy(Long revokedBy) { this.revokedBy = revokedBy; }
}
