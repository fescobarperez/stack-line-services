package com.erp_maya.salud.dto;

import com.erp_maya.partner.dto.ClientDtos;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pacientes de Stackline Salud. Un paciente es un cliente: lo que Cliente ya
 * tiene (nombre, NIT, teléfono, correo, dirección) viaja en `client` con el
 * mismo formato de /api/clients; aquí solo va lo propio del paciente.
 *
 * Las vistas con listas llevan {@code JsonInclude(NON_NULL)}: Serde omite por
 * defecto las listas vacías y el front espera {@code []}.
 */
public final class PatientDtos {

    private PatientDtos() {}

    // ── Entrada ──────────────────────────────────────────────────────────

    /** Ficha administrativa del paciente (sin datos clínicos). */
    @Serdeable
    public record PatientData(
            @Nullable String docType,
            @Nullable @Size(max = 30) String docNumber,
            @Nullable @Size(max = 80) String preferredName,
            @Nullable LocalDate birthDate,
            @Nullable String sex,
            @Nullable @Size(max = 40) String gender,
            @Nullable @Size(max = 20) String maritalStatus,
            @Nullable @Size(max = 60) String nationality,
            @Nullable @Size(max = 100) String occupation,
            @Nullable @Size(max = 60) String education,
            @Nullable @Size(max = 60) String referralSource,
            @Nullable @Size(max = 150) String referredBy,
            @Nullable Long primaryProfessionalId,
            @Nullable String reminderChannel,
            @Nullable Boolean acceptsMessages,
            @Nullable @Size(max = 120) String insurerName,
            @Nullable @Size(max = 60) String policyNumber,
            @Nullable LocalDate policyExpiresOn,
            @Nullable @Size(max = 120) String agreementName,
            @Nullable @Size(max = 2000) String adminNotes,
            @Nullable Map<String, Object> specialtyData,
            @Nullable @Valid List<GuardianData> guardians,
            @Nullable @Valid List<EmergencyContactData> emergencyContacts) {}

    /** Responsable: otro cliente que es tutor legal y/o quien paga. */
    @Serdeable
    public record GuardianData(
            @NotNull Long clientId,
            @NotBlank @Size(max = 40) String relationship,
            boolean legalGuardian,
            boolean payer) {}

    @Serdeable
    public record EmergencyContactData(
            @NotBlank @Size(max = 150) String name,
            @Nullable @Size(max = 40) String relationship,
            @NotBlank @Size(max = 40) String phone) {}

    /** Datos clínicos base. Requieren el permiso de expediente clínico. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClinicalData(
            @Nullable String bloodType,
            @Nullable @Size(max = 60) String religion,
            @Nullable @Size(max = 500) String disability,
            @Nullable @Size(max = 500) String specialNeeds,
            @Nullable @Size(max = 200) String smoking,
            @Nullable @Size(max = 200) String alcohol,
            @Nullable @Size(max = 200) String physicalActivity,
            @Nullable @Size(max = 200) String diet,
            @Nullable @Size(max = 1000) String gynecoObstetric,
            @Nullable @Size(max = 500) String alerts,
            @Nullable @Size(max = 1000) String advanceDirectives,
            @Nullable @Valid List<AllergyData> allergies,
            @Nullable @Valid List<HistoryData> history,
            @Nullable @Valid List<MedicationData> medications,
            @Nullable @Valid List<VaccineData> vaccines) {}

    @Serdeable
    public record AllergyData(
            @NotBlank String kind,
            @NotBlank @Size(max = 150) String allergen,
            @Nullable @Size(max = 300) String reaction,
            @Nullable String severity,
            @Nullable @Size(max = 500) String notes) {}

    @Serdeable
    public record HistoryData(
            @NotBlank String kind,
            @NotBlank @Size(max = 500) String description,
            @Nullable @Size(max = 10) String cie10Code,
            @Nullable @Size(max = 60) String relative,
            @Nullable @Size(max = 60) String occurred,
            @Nullable @Size(max = 500) String notes) {}

    @Serdeable
    public record MedicationData(
            @NotBlank @Size(max = 150) String name,
            @Nullable @Size(max = 80) String dose,
            @Nullable @Size(max = 80) String frequency,
            @Nullable @Size(max = 60) String since,
            @Nullable @Size(max = 500) String notes) {}

    @Serdeable
    public record VaccineData(
            @NotBlank @Size(max = 120) String vaccine,
            @Nullable @Size(max = 40) String dose,
            @Nullable LocalDate appliedOn,
            @Nullable @Size(max = 300) String notes) {}

    /** Alta unificada: crea el cliente y el paciente en una sola transacción. */
    @Serdeable
    public record CreateRequest(
            @NotNull @Valid ClientDtos.Request client,
            @NotNull @Valid PatientData patient,
            @Nullable @Valid ClinicalData clinical) {}

    /** Convierte en paciente a un cliente que ya existe. */
    @Serdeable
    public record FromClientRequest(
            @NotNull @Valid PatientData patient,
            @Nullable @Valid ClinicalData clinical) {}

    /** Edición de la ficha: actualiza el cliente y el paciente juntos. */
    @Serdeable
    public record UpdateRequest(
            @NotNull @Valid ClientDtos.Request client,
            @NotNull @Valid PatientData patient) {}

    @Serdeable
    public record StatusRequest(@NotBlank String status, @Nullable LocalDate deceasedOn) {}

    @Serdeable
    public record ConsentRequest(
            @NotBlank String kind,
            @Nullable @Size(max = 30) String version,
            @NotNull Boolean granted,
            @Nullable Instant signedAt,
            @Nullable @Size(max = 500) String notes) {}

    // ── Salida ───────────────────────────────────────────────────────────

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PatientView(
            Long id,
            String recordNumber,
            @Nullable String docType,
            @Nullable String docNumber,
            @Nullable String preferredName,
            @Nullable LocalDate birthDate,
            @Nullable Integer age,
            @Nullable String sex,
            @Nullable String gender,
            @Nullable String maritalStatus,
            @Nullable String nationality,
            @Nullable String occupation,
            @Nullable String education,
            @Nullable String referralSource,
            @Nullable String referredBy,
            @Nullable Long primaryProfessionalId,
            String status,
            @Nullable LocalDate deceasedOn,
            String reminderChannel,
            boolean acceptsMessages,
            @Nullable String insurerName,
            @Nullable String policyNumber,
            @Nullable LocalDate policyExpiresOn,
            @Nullable String agreementName,
            @Nullable String adminNotes,
            Map<String, Object> specialtyData,
            Instant createdAt) {}

    @Serdeable
    public record GuardianView(
            Long clientId,
            String name,
            @Nullable String nit,
            @Nullable String phone,
            String relationship,
            boolean legalGuardian,
            boolean payer) {}

    @Serdeable
    public record ConsentView(
            Long id,
            String kind,
            @Nullable String version,
            boolean granted,
            Instant signedAt,
            @Nullable String notes,
            Instant recordedAt,
            @Nullable Instant revokedAt) {}

    /**
     * Expediente completo. `clinical` es null cuando el usuario no tiene el
     * permiso de expediente clínico; `clinicalVisible` le dice a la pantalla
     * si debe mostrar la pestaña, sin confundir "sin permiso" con "vacío".
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            ClientDtos.Response client,
            PatientView patient,
            List<GuardianView> guardians,
            List<EmergencyContactData> emergencyContacts,
            List<ConsentView> consents,
            boolean clinicalVisible,
            @Nullable ClinicalData clinical,
            @Nullable Instant clinicalUpdatedAt) {}

    @Serdeable
    public record ListItem(
            Long id,
            String recordNumber,
            String name,
            @Nullable String docType,
            @Nullable String docNumber,
            @Nullable String nit,
            @Nullable String phone,
            @Nullable String email,
            @Nullable LocalDate birthDate,
            @Nullable Integer age,
            @Nullable String sex,
            String status) {}

    @Serdeable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ListPage(List<ListItem> content, long totalSize, int page, int size) {}

    /**
     * Resultado de "buscar antes de crear": un cliente que coincide y, si ya
     * es paciente, su número de expediente.
     */
    @Serdeable
    public record Candidate(
            Long clientId,
            String name,
            @Nullable String nit,
            @Nullable String phone,
            @Nullable String email,
            boolean patient,
            @Nullable String recordNumber) {}

    /** Cuáles de unos clientes ya son pacientes (para la etiqueta «Paciente» en Clientes). */
    @Serdeable
    public record ClientLink(Long clientId, String recordNumber, String status) {}

    @Serdeable
    public record AccessEntry(@Nullable Long userId, @Nullable String userName, String action, Instant occurredAt) {}
}
