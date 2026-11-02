package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Paciente: extensión 1:1 de un cliente (misma llave). Ver 089_salud_pacientes.sql. */
@Entity
@Table(name = "salud_patients")
public class Patient {

    @Id
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "record_number", nullable = false)
    private String recordNumber;

    @Column(name = "doc_type")
    private String docType;

    @Column(name = "doc_number")
    private String docNumber;

    @Column(name = "preferred_name")
    private String preferredName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String sex;

    private String gender;

    @Column(name = "marital_status")
    private String maritalStatus;

    private String nationality;

    private String occupation;

    private String education;

    @Column(name = "referral_source")
    private String referralSource;

    @Column(name = "referred_by")
    private String referredBy;

    @Column(name = "primary_professional_id")
    private Long primaryProfessionalId;

    @Column(nullable = false)
    private String status;

    @Column(name = "deceased_on")
    private LocalDate deceasedOn;

    @Column(name = "reminder_channel", nullable = false)
    private String reminderChannel;

    @Column(name = "accepts_messages", nullable = false)
    private boolean acceptsMessages;

    @Column(name = "insurer_name")
    private String insurerName;

    @Column(name = "policy_number")
    private String policyNumber;

    @Column(name = "policy_expires_on")
    private LocalDate policyExpiresOn;

    @Column(name = "agreement_name")
    private String agreementName;

    @Column(name = "admin_notes")
    private String adminNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specialty_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> specialtyData = new HashMap<>();

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getRecordNumber() { return recordNumber; }
    public void setRecordNumber(String recordNumber) { this.recordNumber = recordNumber; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getDocNumber() { return docNumber; }
    public void setDocNumber(String docNumber) { this.docNumber = docNumber; }

    public String getPreferredName() { return preferredName; }
    public void setPreferredName(String preferredName) { this.preferredName = preferredName; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }

    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }

    public String getReferralSource() { return referralSource; }
    public void setReferralSource(String referralSource) { this.referralSource = referralSource; }

    public String getReferredBy() { return referredBy; }
    public void setReferredBy(String referredBy) { this.referredBy = referredBy; }

    public Long getPrimaryProfessionalId() { return primaryProfessionalId; }
    public void setPrimaryProfessionalId(Long primaryProfessionalId) { this.primaryProfessionalId = primaryProfessionalId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getDeceasedOn() { return deceasedOn; }
    public void setDeceasedOn(LocalDate deceasedOn) { this.deceasedOn = deceasedOn; }

    public String getReminderChannel() { return reminderChannel; }
    public void setReminderChannel(String reminderChannel) { this.reminderChannel = reminderChannel; }

    public boolean isAcceptsMessages() { return acceptsMessages; }
    public void setAcceptsMessages(boolean acceptsMessages) { this.acceptsMessages = acceptsMessages; }

    public String getInsurerName() { return insurerName; }
    public void setInsurerName(String insurerName) { this.insurerName = insurerName; }

    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }

    public LocalDate getPolicyExpiresOn() { return policyExpiresOn; }
    public void setPolicyExpiresOn(LocalDate policyExpiresOn) { this.policyExpiresOn = policyExpiresOn; }

    public String getAgreementName() { return agreementName; }
    public void setAgreementName(String agreementName) { this.agreementName = agreementName; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public Map<String, Object> getSpecialtyData() { return specialtyData; }
    public void setSpecialtyData(Map<String, Object> specialtyData) { this.specialtyData = specialtyData; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
