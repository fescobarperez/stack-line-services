package com.erp_maya.salud.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.dto.ClientDtos;
import com.erp_maya.partner.service.ClientService;
import com.erp_maya.salud.domain.AccessLog;
import com.erp_maya.salud.domain.EmergencyContact;
import com.erp_maya.salud.domain.Patient;
import com.erp_maya.salud.domain.PatientAllergy;
import com.erp_maya.salud.domain.PatientClinical;
import com.erp_maya.salud.domain.PatientConsent;
import com.erp_maya.salud.domain.PatientGuardian;
import com.erp_maya.salud.domain.PatientHistory;
import com.erp_maya.salud.domain.PatientMedication;
import com.erp_maya.salud.domain.PatientRow;
import com.erp_maya.salud.domain.PatientVaccine;
import com.erp_maya.salud.dto.PatientDtos;
import com.erp_maya.salud.repository.AccessLogRepository;
import com.erp_maya.salud.repository.AccessLogRowRepository;
import com.erp_maya.salud.repository.EmergencyContactRepository;
import com.erp_maya.salud.repository.PatientAllergyRepository;
import com.erp_maya.salud.repository.PatientClinicalRepository;
import com.erp_maya.salud.repository.PatientConsentRepository;
import com.erp_maya.salud.repository.PatientGuardianRepository;
import com.erp_maya.salud.repository.PatientHistoryRepository;
import com.erp_maya.salud.repository.PatientMedicationRepository;
import com.erp_maya.salud.repository.PatientRepository;
import com.erp_maya.salud.repository.PatientRowRepository;
import com.erp_maya.salud.repository.PatientVaccineRepository;
import com.erp_maya.security.service.UserService;
import com.erp_maya.sequence.service.DocumentSequenceService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.erp_maya.salud.service.PatientRules.blank;
import static com.erp_maya.salud.service.PatientRules.oneOf;
import static com.erp_maya.salud.service.SaludAccess.CLINICAL;
import static com.erp_maya.salud.service.SaludAccess.CREAR;
import static com.erp_maya.salud.service.SaludAccess.EDITAR;
import static com.erp_maya.salud.service.SaludAccess.PATIENTS;
import static com.erp_maya.salud.service.SaludAccess.VER;

/**
 * Pacientes: un paciente es un cliente con ficha de salud.
 *
 * Lo que es de Cliente (nombre, NIT, teléfono, correo, dirección) se crea y
 * se edita SIEMPRE por ClientService, con sus mismas validaciones; este
 * servicio nunca escribe en `clients`. Así la caja, la FEL y el CRM siguen
 * viendo un cliente normal.
 *
 * Todos los métodos públicos son puntos de entrada: no se llaman entre sí,
 * porque una llamada interna no pasa por el proxy de @Transactional.
 */
@Singleton
public class PatientService {

    /** Tipo de serie en document_sequences: da expedientes EXP-000001, EXP-000002… */
    static final String SERIE_EXPEDIENTE = "EXPEDIENTE";
    private static final int MAX_PAGINA = 100;
    private static final int MAX_CANDIDATOS = 15;
    private static final int MAX_LOOKUP = 500;

    /** Acciones de la bitácora de accesos. */
    static final String VER_FICHA = "ver_ficha";
    static final String VER_CLINICO = "ver_clinico";
    static final String CREAR_PACIENTE = "crear";
    static final String EDITAR_FICHA = "editar_ficha";
    static final String EDITAR_CLINICO = "editar_clinico";
    static final String CAMBIAR_ESTADO = "cambiar_estado";
    static final String CONSENTIMIENTO = "consentimiento";

    private final PatientRepository patients;
    private final PatientRowRepository rows;
    private final PatientClinicalRepository clinicals;
    private final PatientAllergyRepository allergies;
    private final PatientHistoryRepository histories;
    private final PatientMedicationRepository medications;
    private final PatientVaccineRepository vaccines;
    private final PatientGuardianRepository guardians;
    private final EmergencyContactRepository contacts;
    private final PatientConsentRepository consents;
    private final AccessLogRepository accessLog;
    private final AccessLogRowRepository accessRows;
    private final ClientService clients;
    private final UserService users;
    private final DocumentSequenceService sequences;
    private final TenantContext tenant;

    public PatientService(PatientRepository patients, PatientRowRepository rows,
                          PatientClinicalRepository clinicals, PatientAllergyRepository allergies,
                          PatientHistoryRepository histories, PatientMedicationRepository medications,
                          PatientVaccineRepository vaccines, PatientGuardianRepository guardians,
                          EmergencyContactRepository contacts, PatientConsentRepository consents,
                          AccessLogRepository accessLog, AccessLogRowRepository accessRows,
                          ClientService clients, UserService users, DocumentSequenceService sequences,
                          TenantContext tenant) {
        this.patients = patients;
        this.rows = rows;
        this.clinicals = clinicals;
        this.allergies = allergies;
        this.histories = histories;
        this.medications = medications;
        this.vaccines = vaccines;
        this.guardians = guardians;
        this.contacts = contacts;
        this.consents = consents;
        this.accessLog = accessLog;
        this.accessRows = accessRows;
        this.clients = clients;
        this.users = users;
        this.sequences = sequences;
        this.tenant = tenant;
    }

    // ── Consultas ────────────────────────────────────────────────────────

    @Transactional
    public PatientDtos.ListPage list(SaludAccess.Actor actor, @Nullable String search,
                                     @Nullable String status, int page, int size) {
        actor.require(PATIENTS, VER);
        Long companyId = tenant.getCompanyId();
        int tam = Math.max(1, Math.min(size, MAX_PAGINA));
        int pag = Math.max(0, page);
        String estado = status == null || status.isBlank() ? "" : oneOf(status, PatientRules.STATUSES, "Estado");
        String texto = PatientRules.searchText(search);
        String digitos = PatientRules.phoneDigits(search);
        LocalDate hoy = PatientRules.today();
        List<PatientDtos.ListItem> items = rows.search(companyId, estado, texto, digitos, tam, pag * tam)
                .stream().map(r -> toListItem(r, hoy)).toList();
        long total = rows.countSearch(companyId, estado, texto, digitos);
        return new PatientDtos.ListPage(items, total, pag, tam);
    }

    /**
     * «Buscar antes de crear»: clientes que coinciden por teléfono, DPI, NIT o
     * nombre, marcando cuáles ya son pacientes. Evita registrar dos veces a la
     * misma persona: si ya es cliente, se convierte; si ya es paciente, se abre.
     */
    @Transactional
    public List<PatientDtos.Candidate> candidates(SaludAccess.Actor actor, @Nullable String q) {
        actor.require(PATIENTS, CREAR);
        String texto = blank(q);
        if (texto == null) return List.of();
        Long companyId = tenant.getCompanyId();
        Map<Long, ClientDtos.Response> encontrados = new LinkedHashMap<>();

        if (!PatientRules.phoneDigits(texto).isEmpty()) {
            clients.findByPhone(texto).ifPresent(c -> encontrados.put(c.id(), c));
        }
        String dpi = texto.replaceAll("[\\s-]", "");
        if (dpi.matches("\\d{13}")) {
            patients.findByCompanyIdAndDocTypeAndDocNumber(companyId, "DPI", dpi)
                    .ifPresent(p -> encontrados.putIfAbsent(p.getId(), clients.get(p.getId())));
        }
        clients.findByNit(texto).ifPresent(c -> encontrados.putIfAbsent(c.id(), c));
        if (texto.chars().anyMatch(Character::isLetter)) {
            clients.list(texto, Pageable.from(0, MAX_CANDIDATOS)).getContent()
                    .forEach(c -> encontrados.putIfAbsent(c.id(), c));
        }
        if (encontrados.isEmpty()) return List.of();

        Map<Long, String> expedientes = new HashMap<>();
        patients.findByCompanyIdAndIdIn(companyId, encontrados.keySet())
                .forEach(p -> expedientes.put(p.getId(), p.getRecordNumber()));
        return encontrados.values().stream().limit(MAX_CANDIDATOS)
                .map(c -> new PatientDtos.Candidate(c.id(), c.name(), c.nit(), c.phone(), c.email(),
                        expedientes.containsKey(c.id()), expedientes.get(c.id())))
                .toList();
    }

    /**
     * De estos clientes, cuáles son pacientes. Lo usa la pantalla de Clientes
     * para marcar «Paciente» y ofrecer «Ver expediente» o «Convertir en paciente».
     */
    @Transactional
    public List<PatientDtos.ClientLink> lookup(SaludAccess.Actor actor, List<Long> clientIds) {
        actor.require(PATIENTS, VER);
        if (clientIds == null || clientIds.isEmpty()) return List.of();
        List<Long> ids = clientIds.stream().distinct().limit(MAX_LOOKUP).toList();
        return patients.findByCompanyIdAndIdIn(tenant.getCompanyId(), ids).stream()
                .map(p -> new PatientDtos.ClientLink(p.getId(), p.getRecordNumber(), p.getStatus()))
                .toList();
    }

    /** Expediente completo. Abrirlo queda en la bitácora. */
    @Transactional
    public PatientDtos.Detail get(SaludAccess.Actor actor, Long id) {
        actor.require(PATIENTS, VER);
        Patient p = find(id);
        log(actor, p, actor.can(CLINICAL, VER) ? VER_CLINICO : VER_FICHA);
        return detail(actor, p);
    }

    /** Quién abrió o cambió el expediente, lo más reciente primero. */
    @Transactional
    public List<PatientDtos.AccessEntry> accessLog(SaludAccess.Actor actor, Long id, int page, int size) {
        actor.require(CLINICAL, VER);
        Patient p = find(id);
        int tam = Math.max(1, Math.min(size, MAX_PAGINA));
        return accessRows.findByPatientIdAndCompanyId(p.getId(), p.getCompanyId(),
                        Pageable.from(Math.max(0, page), tam, Sort.of(Sort.Order.desc("occurredAt"))))
                .getContent().stream()
                .map(r -> new PatientDtos.AccessEntry(r.getUserId(), r.getUserName(), r.getAction(), r.getOccurredAt()))
                .toList();
    }

    // ── Altas ────────────────────────────────────────────────────────────

    /** Alta unificada: crea el cliente (por ClientService) y el paciente en la misma transacción. */
    @Transactional
    public PatientDtos.Detail create(SaludAccess.Actor actor, PatientDtos.CreateRequest req) {
        actor.require(PATIENTS, CREAR);
        if (req.clinical() != null) actor.require(CLINICAL, EDITAR);
        // Validar antes de crear el cliente: si algo está mal, que no quede nada a medias.
        validatePatient(req.patient(), null);
        ClientDtos.Response client = clients.create(req.client());
        Patient p = newPatient(actor, client.id(), req.patient(), req.clinical());
        return detail(actor, p);
    }

    /** Convierte en paciente a un cliente que ya existe. */
    @Transactional
    public PatientDtos.Detail fromClient(SaludAccess.Actor actor, Long clientId, PatientDtos.FromClientRequest req) {
        actor.require(PATIENTS, CREAR);
        if (req.clinical() != null) actor.require(CLINICAL, EDITAR);
        clients.get(clientId); // 404 si no es un cliente de esta empresa
        patients.findByIdAndCompanyId(clientId, tenant.getCompanyId()).ifPresent(p -> {
            throw new IllegalStateException("Este cliente ya es paciente (expediente " + p.getRecordNumber() + ").");
        });
        validatePatient(req.patient(), clientId);
        Patient p = newPatient(actor, clientId, req.patient(), req.clinical());
        return detail(actor, p);
    }

    // ── Cambios ──────────────────────────────────────────────────────────

    /** Edita la ficha: el cliente por ClientService y lo demás aquí, juntos. */
    @Transactional
    public PatientDtos.Detail update(SaludAccess.Actor actor, Long id, PatientDtos.UpdateRequest req) {
        actor.require(PATIENTS, EDITAR);
        Patient p = find(id);
        validatePatient(req.patient(), p.getId());
        clients.update(p.getId(), req.client());
        applyPatient(p, req.patient());
        patients.update(p);
        replaceGuardians(p, req.patient().guardians());
        replaceContacts(p, req.patient().emergencyContacts());
        log(actor, p, EDITAR_FICHA);
        return detail(actor, p);
    }

    /** Reemplaza los datos clínicos base y sus listas (alergias, antecedentes, medicamentos, vacunas). */
    @Transactional
    public PatientDtos.Detail updateClinical(SaludAccess.Actor actor, Long id, PatientDtos.ClinicalData data) {
        actor.require(CLINICAL, EDITAR);
        Patient p = find(id);
        saveClinical(actor, p, data);
        log(actor, p, EDITAR_CLINICO);
        return detail(actor, p);
    }

    /**
     * Activo, inactivo o fallecido. No hay borrado: el expediente es un
     * registro clínico y se conserva; inactivar lo saca de las búsquedas.
     */
    @Transactional
    public PatientDtos.Detail changeStatus(SaludAccess.Actor actor, Long id, PatientDtos.StatusRequest req) {
        actor.require(PATIENTS, EDITAR);
        Patient p = find(id);
        String status = oneOf(req.status(), PatientRules.STATUSES, "Estado");
        p.setStatus(status);
        p.setDeceasedOn(PatientRules.deceasedOn(status, req.deceasedOn(), PatientRules.today()));
        patients.update(p);
        log(actor, p, CAMBIAR_ESTADO);
        return detail(actor, p);
    }

    /** Registra un consentimiento firmado. No se edita: si cambia, se revoca y se registra otro. */
    @Transactional
    public PatientDtos.Detail addConsent(SaludAccess.Actor actor, Long id, PatientDtos.ConsentRequest req) {
        actor.require(PATIENTS, EDITAR);
        Patient p = find(id);
        Instant ahora = Instant.now();
        Instant firmado = req.signedAt() != null ? req.signedAt() : ahora;
        if (firmado.isAfter(ahora)) {
            throw new IllegalStateException("La fecha de firma no puede ser futura.");
        }
        PatientConsent c = new PatientConsent();
        c.setPatientId(p.getId());
        c.setCompanyId(p.getCompanyId());
        c.setKind(oneOf(req.kind(), PatientRules.CONSENT_KINDS, "Tipo de consentimiento"));
        c.setVersion(blank(req.version()));
        c.setGranted(req.granted());
        c.setSignedAt(firmado);
        c.setNotes(blank(req.notes()));
        c.setRecordedBy(actor.userId());
        c.setRecordedAt(ahora);
        consents.save(c);
        log(actor, p, CONSENTIMIENTO);
        return detail(actor, p);
    }

    @Transactional
    public PatientDtos.Detail revokeConsent(SaludAccess.Actor actor, Long id, Long consentId) {
        actor.require(PATIENTS, EDITAR);
        Patient p = find(id);
        PatientConsent c = consents.findByIdAndPatientIdAndCompanyId(consentId, p.getId(), p.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Consentimiento " + consentId + " no encontrado"));
        if (c.getRevokedAt() != null) {
            throw new IllegalStateException("Este consentimiento ya estaba revocado.");
        }
        c.setRevokedAt(Instant.now());
        c.setRevokedBy(actor.userId());
        consents.update(c);
        log(actor, p, CONSENTIMIENTO);
        return detail(actor, p);
    }

    // ── Internos ─────────────────────────────────────────────────────────

    private Patient find(Long id) {
        return patients.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente " + id + " no encontrado"));
    }

    /**
     * Reglas que necesitan la base: documento no repetido, profesional y
     * responsables de esta empresa. Las puras están en PatientRules.
     */
    private void validatePatient(PatientDtos.PatientData d, @Nullable Long patientId) {
        LocalDate hoy = PatientRules.today();
        PatientRules.validateBirthDate(d.birthDate(), hoy);
        String[] doc = PatientRules.document(d.docType(), d.docNumber());
        if (doc[0] != null) {
            patients.findByCompanyIdAndDocTypeAndDocNumber(tenant.getCompanyId(), doc[0], doc[1])
                    .filter(otro -> !otro.getId().equals(patientId))
                    .ifPresent(otro -> {
                        throw new IllegalStateException("Ya hay un paciente con ese " + doc[0]
                                + " (expediente " + otro.getRecordNumber() + ").");
                    });
        }
        oneOf(d.sex(), PatientRules.SEXES, "Sexo");
        oneOf(d.reminderChannel(), PatientRules.CHANNELS, "Canal de recordatorio");
        PatientRules.validateGuardians(d.guardians(), patientId, PatientRules.age(d.birthDate(), hoy));
        if (d.guardians() != null) {
            d.guardians().forEach(g -> clients.get(g.clientId())); // 404 si no es cliente de esta empresa
        }
        if (d.primaryProfessionalId() != null) {
            users.get(d.primaryProfessionalId()); // 404 si no es usuario de esta empresa
        }
    }

    private Patient newPatient(SaludAccess.Actor actor, Long clientId, PatientDtos.PatientData d,
                               @Nullable PatientDtos.ClinicalData clinical) {
        Patient p = new Patient();
        p.setId(clientId);
        p.setCompanyId(tenant.getCompanyId());
        p.setRecordNumber(sequences.next(SERIE_EXPEDIENTE, null));
        p.setStatus("activo");
        p.setCreatedBy(actor.userId());
        applyPatient(p, d);
        patients.save(p);
        // El paciente usa id asignado (el del cliente), así que Hibernate deja su
        // INSERT para el flush; las tablas hijas usan IDENTITY y se insertan al
        // momento. Sin este flush, la FK de la primera hija no encuentra al paciente.
        patients.flush();
        replaceGuardians(p, d.guardians());
        replaceContacts(p, d.emergencyContacts());
        if (clinical != null) {
            saveClinical(actor, p, clinical);
        }
        log(actor, p, CREAR_PACIENTE);
        return p;
    }

    /** Copia la ficha al paciente. El estado no se toca aquí: tiene su propio endpoint. */
    private static void applyPatient(Patient p, PatientDtos.PatientData d) {
        String[] doc = PatientRules.document(d.docType(), d.docNumber());
        p.setDocType(doc[0]);
        p.setDocNumber(doc[1]);
        p.setPreferredName(blank(d.preferredName()));
        p.setBirthDate(d.birthDate());
        p.setSex(oneOf(d.sex(), PatientRules.SEXES, "Sexo"));
        p.setGender(blank(d.gender()));
        p.setMaritalStatus(blank(d.maritalStatus()));
        p.setNationality(blank(d.nationality()));
        p.setOccupation(blank(d.occupation()));
        p.setEducation(blank(d.education()));
        p.setReferralSource(blank(d.referralSource()));
        p.setReferredBy(blank(d.referredBy()));
        p.setPrimaryProfessionalId(d.primaryProfessionalId());
        String canal = oneOf(d.reminderChannel(), PatientRules.CHANNELS, "Canal de recordatorio");
        p.setReminderChannel(canal != null ? canal : "whatsapp");
        p.setAcceptsMessages(Boolean.TRUE.equals(d.acceptsMessages()));
        p.setInsurerName(blank(d.insurerName()));
        p.setPolicyNumber(blank(d.policyNumber()));
        p.setPolicyExpiresOn(d.policyExpiresOn());
        p.setAgreementName(blank(d.agreementName()));
        p.setAdminNotes(blank(d.adminNotes()));
        p.setSpecialtyData(d.specialtyData() != null ? new HashMap<>(d.specialtyData()) : new HashMap<>());
    }

    private void replaceGuardians(Patient p, @Nullable List<PatientDtos.GuardianData> list) {
        guardians.deleteByPatientId(p.getId());
        if (list == null) return;
        for (PatientDtos.GuardianData g : list) {
            PatientGuardian e = new PatientGuardian();
            e.setPatientId(p.getId());
            e.setCompanyId(p.getCompanyId());
            e.setClientId(g.clientId());
            e.setRelationship(g.relationship().trim());
            e.setLegalGuardian(g.legalGuardian());
            e.setPayer(g.payer());
            guardians.save(e);
        }
    }

    private void replaceContacts(Patient p, @Nullable List<PatientDtos.EmergencyContactData> list) {
        contacts.deleteByPatientId(p.getId());
        if (list == null) return;
        int orden = 0;
        for (PatientDtos.EmergencyContactData c : list) {
            EmergencyContact e = new EmergencyContact();
            e.setPatientId(p.getId());
            e.setCompanyId(p.getCompanyId());
            e.setName(c.name().trim());
            e.setRelationship(blank(c.relationship()));
            e.setPhone(c.phone().trim());
            e.setSortOrder(orden++);
            contacts.save(e);
        }
    }

    private void saveClinical(SaludAccess.Actor actor, Patient p, PatientDtos.ClinicalData d) {
        PatientRules.validateHistory(d.history());
        PatientClinical c = clinicals.findByPatientIdAndCompanyId(p.getId(), p.getCompanyId()).orElse(null);
        boolean nuevo = c == null;
        if (nuevo) {
            c = new PatientClinical();
            c.setPatientId(p.getId());
            c.setCompanyId(p.getCompanyId());
        }
        c.setBloodType(oneOf(d.bloodType(), PatientRules.BLOOD_TYPES, "Tipo de sangre"));
        c.setReligion(blank(d.religion()));
        c.setDisability(blank(d.disability()));
        c.setSpecialNeeds(blank(d.specialNeeds()));
        c.setSmoking(blank(d.smoking()));
        c.setAlcohol(blank(d.alcohol()));
        c.setPhysicalActivity(blank(d.physicalActivity()));
        c.setDiet(blank(d.diet()));
        c.setGynecoObstetric(blank(d.gynecoObstetric()));
        c.setAlerts(blank(d.alerts()));
        c.setAdvanceDirectives(blank(d.advanceDirectives()));
        c.setUpdatedBy(actor.userId());
        if (nuevo) clinicals.save(c); else clinicals.update(c);

        allergies.deleteByPatientId(p.getId());
        for (PatientDtos.AllergyData a : nn(d.allergies())) {
            PatientAllergy e = new PatientAllergy();
            e.setPatientId(p.getId());
            e.setCompanyId(p.getCompanyId());
            e.setKind(oneOf(a.kind(), PatientRules.ALLERGY_KINDS, "Tipo de alergia"));
            e.setAllergen(a.allergen().trim());
            e.setReaction(blank(a.reaction()));
            e.setSeverity(oneOf(a.severity(), PatientRules.SEVERITIES, "Severidad"));
            e.setNotes(blank(a.notes()));
            allergies.save(e);
        }
        histories.deleteByPatientId(p.getId());
        for (PatientDtos.HistoryData h : nn(d.history())) {
            PatientHistory e = new PatientHistory();
            e.setPatientId(p.getId());
            e.setCompanyId(p.getCompanyId());
            e.setKind(oneOf(h.kind(), PatientRules.HISTORY_KINDS, "Tipo de antecedente"));
            e.setDescription(h.description().trim());
            String cie = blank(h.cie10Code());
            e.setCie10Code(cie == null ? null : cie.toUpperCase());
            e.setRelative(blank(h.relative()));
            e.setOccurred(blank(h.occurred()));
            e.setNotes(blank(h.notes()));
            histories.save(e);
        }
        medications.deleteByPatientId(p.getId());
        for (PatientDtos.MedicationData m : nn(d.medications())) {
            PatientMedication e = new PatientMedication();
            e.setPatientId(p.getId());
            e.setCompanyId(p.getCompanyId());
            e.setName(m.name().trim());
            e.setDose(blank(m.dose()));
            e.setFrequency(blank(m.frequency()));
            e.setSince(blank(m.since()));
            e.setNotes(blank(m.notes()));
            medications.save(e);
        }
        vaccines.deleteByPatientId(p.getId());
        for (PatientDtos.VaccineData v : nn(d.vaccines())) {
            PatientVaccine e = new PatientVaccine();
            e.setPatientId(p.getId());
            e.setCompanyId(p.getCompanyId());
            e.setVaccine(v.vaccine().trim());
            e.setDose(blank(v.dose()));
            e.setAppliedOn(v.appliedOn());
            e.setNotes(blank(v.notes()));
            vaccines.save(e);
        }
    }

    private void log(SaludAccess.Actor actor, Patient p, String action) {
        AccessLog l = new AccessLog();
        l.setCompanyId(p.getCompanyId());
        l.setPatientId(p.getId());
        l.setUserId(actor.userId());
        l.setAction(action);
        l.setOccurredAt(Instant.now());
        accessLog.save(l);
    }

    /** Arma el expediente. Lo clínico solo se carga si el usuario puede verlo. */
    private PatientDtos.Detail detail(SaludAccess.Actor actor, Patient p) {
        ClientDtos.Response client = clients.get(p.getId());
        List<PatientDtos.GuardianView> gs = new ArrayList<>();
        for (PatientGuardian g : guardians.findByPatientIdOrderById(p.getId())) {
            ClientDtos.Response c = clients.get(g.getClientId());
            gs.add(new PatientDtos.GuardianView(c.id(), c.name(), c.nit(), c.phone(),
                    g.getRelationship(), g.isLegalGuardian(), g.isPayer()));
        }
        List<PatientDtos.EmergencyContactData> cs = contacts.findByPatientIdOrderBySortOrder(p.getId()).stream()
                .map(c -> new PatientDtos.EmergencyContactData(c.getName(), c.getRelationship(), c.getPhone()))
                .toList();
        List<PatientDtos.ConsentView> cons = consents.findByPatientIdOrderBySignedAtDesc(p.getId()).stream()
                .map(c -> new PatientDtos.ConsentView(c.getId(), c.getKind(), c.getVersion(), c.isGranted(),
                        c.getSignedAt(), c.getNotes(), c.getRecordedAt(), c.getRevokedAt()))
                .toList();

        boolean visible = actor.can(CLINICAL, VER);
        PatientDtos.ClinicalData clinical = null;
        Instant clinicalUpdatedAt = null;
        if (visible) {
            PatientClinical c = clinicals.findByPatientIdAndCompanyId(p.getId(), p.getCompanyId()).orElse(null);
            clinical = toClinical(c, p.getId());
            clinicalUpdatedAt = c != null ? c.getUpdatedAt() : null;
        }
        return new PatientDtos.Detail(client, toView(p), gs, cs, cons, visible, clinical, clinicalUpdatedAt);
    }

    private PatientDtos.ClinicalData toClinical(@Nullable PatientClinical c, Long patientId) {
        List<PatientDtos.AllergyData> al = allergies.findByPatientIdOrderById(patientId).stream()
                .map(a -> new PatientDtos.AllergyData(a.getKind(), a.getAllergen(), a.getReaction(), a.getSeverity(), a.getNotes()))
                .toList();
        List<PatientDtos.HistoryData> hi = histories.findByPatientIdOrderById(patientId).stream()
                .map(h -> new PatientDtos.HistoryData(h.getKind(), h.getDescription(), h.getCie10Code(),
                        h.getRelative(), h.getOccurred(), h.getNotes()))
                .toList();
        List<PatientDtos.MedicationData> me = medications.findByPatientIdOrderById(patientId).stream()
                .map(m -> new PatientDtos.MedicationData(m.getName(), m.getDose(), m.getFrequency(), m.getSince(), m.getNotes()))
                .toList();
        List<PatientDtos.VaccineData> va = vaccines.findByPatientIdOrderById(patientId).stream()
                .map(v -> new PatientDtos.VaccineData(v.getVaccine(), v.getDose(), v.getAppliedOn(), v.getNotes()))
                .toList();
        if (c == null) {
            return new PatientDtos.ClinicalData(null, null, null, null, null, null, null, null, null, null, null,
                    al, hi, me, va);
        }
        return new PatientDtos.ClinicalData(c.getBloodType(), c.getReligion(), c.getDisability(), c.getSpecialNeeds(),
                c.getSmoking(), c.getAlcohol(), c.getPhysicalActivity(), c.getDiet(), c.getGynecoObstetric(),
                c.getAlerts(), c.getAdvanceDirectives(), al, hi, me, va);
    }

    private static PatientDtos.PatientView toView(Patient p) {
        return new PatientDtos.PatientView(p.getId(), p.getRecordNumber(), p.getDocType(), p.getDocNumber(),
                p.getPreferredName(), p.getBirthDate(), PatientRules.age(p.getBirthDate(), PatientRules.today()),
                p.getSex(), p.getGender(), p.getMaritalStatus(), p.getNationality(), p.getOccupation(),
                p.getEducation(), p.getReferralSource(), p.getReferredBy(), p.getPrimaryProfessionalId(),
                p.getStatus(), p.getDeceasedOn(), p.getReminderChannel(), p.isAcceptsMessages(),
                p.getInsurerName(), p.getPolicyNumber(), p.getPolicyExpiresOn(), p.getAgreementName(),
                p.getAdminNotes(), p.getSpecialtyData() != null ? p.getSpecialtyData() : Map.of(),
                p.getCreatedAt() != null ? p.getCreatedAt() : Instant.now());
    }

    private static PatientDtos.ListItem toListItem(PatientRow r, LocalDate hoy) {
        return new PatientDtos.ListItem(r.getId(), r.getRecordNumber(), r.getName(), r.getDocType(), r.getDocNumber(),
                r.getNit(), r.getPhone(), r.getEmail(), r.getBirthDate(), PatientRules.age(r.getBirthDate(), hoy),
                r.getSex(), r.getStatus());
    }

    private static <T> List<T> nn(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }
}
