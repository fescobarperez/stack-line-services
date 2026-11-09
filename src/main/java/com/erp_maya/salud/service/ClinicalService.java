package com.erp_maya.salud.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.salud.domain.AccessLog;
import com.erp_maya.salud.domain.Appointment;
import com.erp_maya.salud.domain.ClinicalCase;
import com.erp_maya.salud.domain.Encounter;
import com.erp_maya.salud.domain.EncounterAddendum;
import com.erp_maya.salud.domain.EncounterDiagnosis;
import com.erp_maya.salud.domain.EncounterRow;
import com.erp_maya.salud.domain.FollowUp;
import com.erp_maya.salud.domain.PatientMedication;
import com.erp_maya.salud.domain.PatientRow;
import com.erp_maya.salud.domain.PrescriptionItem;
import com.erp_maya.salud.dto.ClinicalDtos;
import com.erp_maya.salud.repository.AccessLogRepository;
import com.erp_maya.salud.repository.AppointmentRepository;
import com.erp_maya.salud.repository.AppointmentTypeRepository;
import com.erp_maya.salud.repository.Cie10Repository;
import com.erp_maya.salud.repository.ClinicalCaseRepository;
import com.erp_maya.salud.repository.EncounterAddendumRepository;
import com.erp_maya.salud.repository.EncounterDiagnosisRepository;
import com.erp_maya.salud.repository.EncounterRepository;
import com.erp_maya.salud.repository.EncounterRowRepository;
import com.erp_maya.salud.repository.FollowUpRepository;
import com.erp_maya.salud.repository.PatientAllergyRepository;
import com.erp_maya.salud.repository.PatientMedicationRepository;
import com.erp_maya.salud.repository.PatientRowRepository;
import com.erp_maya.salud.repository.PrescriptionItemRepository;
import com.erp_maya.security.dto.UserDtos;
import com.erp_maya.security.service.UserService;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.erp_maya.salud.service.ClinicalRules.BORRADOR;
import static com.erp_maya.salud.service.ClinicalRules.CASO_ABIERTO;
import static com.erp_maya.salud.service.ClinicalRules.CASO_CERRADO;
import static com.erp_maya.salud.service.ClinicalRules.FIRMADA;
import static com.erp_maya.salud.service.ClinicalRules.FU_AGENDADO;
import static com.erp_maya.salud.service.ClinicalRules.FU_CANCELADO;
import static com.erp_maya.salud.service.ClinicalRules.FU_CUMPLIDO;
import static com.erp_maya.salud.service.ClinicalRules.FU_PENDIENTE;
import static com.erp_maya.salud.service.ClinicalRules.blank;
import static com.erp_maya.salud.service.SaludAccess.CLINICAL;
import static com.erp_maya.salud.service.SaludAccess.CREAR;
import static com.erp_maya.salud.service.SaludAccess.EDITAR;
import static com.erp_maya.salud.service.SaludAccess.VER;

/**
 * Atención clínica: la consulta (nota SOAP, signos, diagnósticos, receta),
 * el caso que une las consultas de un mismo problema y los controles.
 *
 * Flujo: la consulta se abre desde la cita cuando el paciente está en sala
 * (la cita pasa a «en atención»), se guarda como borrador las veces que haga
 * falta y se firma. Al firmar: se crea el caso si es nuevo, la receta pasa a
 * los medicamentos de uso actual, el plan de seguimiento crea el control
 * pendiente, el control que esta cita cumplía queda cumplido y la cita se
 * completa. Firmada ya no se edita: las correcciones van como adenda.
 *
 * Todo pide el permiso del expediente clínico; el asistente (TINO) no entra.
 */
@Singleton
public class ClinicalService {

    static final String LOG_ABRIR = "abrir_consulta";
    static final String LOG_VER = "ver_consulta";
    static final String LOG_FIRMAR = "firmar_consulta";
    static final String LOG_ADENDA = "adenda";
    static final String LOG_HISTORIA = "ver_consultas";
    static final String LOG_RECETA = "receta";

    private final EncounterRepository encounters;
    private final EncounterRowRepository encounterRows;
    private final EncounterDiagnosisRepository diagnoses;
    private final PrescriptionItemRepository prescriptions;
    private final EncounterAddendumRepository addenda;
    private final ClinicalCaseRepository cases;
    private final FollowUpRepository followUps;
    private final Cie10Repository cie10;
    private final AppointmentRepository appointments;
    private final AppointmentTypeRepository types;
    private final PatientRowRepository patientRows;
    private final PatientAllergyRepository allergies;
    private final PatientMedicationRepository medications;
    private final AccessLogRepository accessLog;
    private final UserService users;
    private final TenantContext tenant;

    public ClinicalService(EncounterRepository encounters, EncounterRowRepository encounterRows,
                           EncounterDiagnosisRepository diagnoses, PrescriptionItemRepository prescriptions,
                           EncounterAddendumRepository addenda, ClinicalCaseRepository cases,
                           FollowUpRepository followUps, Cie10Repository cie10,
                           AppointmentRepository appointments, AppointmentTypeRepository types,
                           PatientRowRepository patientRows, PatientAllergyRepository allergies,
                           PatientMedicationRepository medications, AccessLogRepository accessLog,
                           UserService users, TenantContext tenant) {
        this.encounters = encounters;
        this.encounterRows = encounterRows;
        this.diagnoses = diagnoses;
        this.prescriptions = prescriptions;
        this.addenda = addenda;
        this.cases = cases;
        this.followUps = followUps;
        this.cie10 = cie10;
        this.appointments = appointments;
        this.types = types;
        this.patientRows = patientRows;
        this.allergies = allergies;
        this.medications = medications;
        this.accessLog = accessLog;
        this.users = users;
        this.tenant = tenant;
    }

    // ── Consulta ─────────────────────────────────────────────────────────

    /**
     * Abre la consulta de una cita (o devuelve la que ya tiene). El paciente
     * tiene que haber llegado: si está en sala, la cita pasa a «en atención».
     * Si la cita es un control, la consulta hereda el caso.
     */
    @Transactional
    public ClinicalDtos.EncounterView openForAppointment(SaludAccess.Actor actor, Long appointmentId) {
        requireClinical(actor, CREAR);
        Long companyId = tenant.getCompanyId();
        Appointment a = appointments.findByIdAndCompanyId(appointmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita " + appointmentId + " no encontrada"));
        Encounter existente = encounters.findByAppointmentId(a.getId()).orElse(null);
        if (existente != null) {
            log(actor, existente.getPatientId(), LOG_VER);
            return view(actor, existente);
        }
        if (!ClinicalRules.CITA_ATENDIBLE.contains(a.getStatus())) {
            throw new IllegalStateException("La consulta se abre cuando el paciente está en sala (la cita está «"
                    + a.getStatus() + "»).");
        }
        Instant ahora = Instant.now();
        if (AgendaRules.EN_SALA.equals(a.getStatus())) {
            a.setStatus(AgendaRules.EN_ATENCION);
            a.setStartedAt(ahora);
            a.setStatusChangedBy(actor.userId());
            appointments.update(a);
        }
        Encounter e = new Encounter();
        e.setCompanyId(companyId);
        e.setPatientId(a.getPatientId());
        e.setAppointmentId(a.getId());
        e.setProfessionalId(a.getProfessionalId());
        e.setCaseId(openCaseOrNull(a.getCaseId(), a.getPatientId()));
        e.setStatus(BORRADOR);
        // El motivo que anotó recepción es el primer borrador del subjetivo.
        e.setSubjective(blank(a.getReason()));
        e.setCreatedBy(actor.userId());
        e.setCreatedAt(ahora);
        e.setUpdatedAt(ahora);
        encounters.save(e);
        encounters.flush();
        log(actor, e.getPatientId(), LOG_ABRIR);
        return view(actor, e);
    }

    @Transactional
    public ClinicalDtos.EncounterView get(SaludAccess.Actor actor, Long id) {
        requireClinical(actor, VER);
        Encounter e = find(id);
        log(actor, e.getPatientId(), LOG_VER);
        return view(actor, e);
    }

    /** Guarda el borrador tal como viene (listas completas). */
    @Transactional
    public ClinicalDtos.EncounterView save(SaludAccess.Actor actor, Long id, ClinicalDtos.EncounterRequest req) {
        requireClinical(actor, EDITAR);
        Encounter e = find(id);
        apply(e, req);
        encounters.update(e);
        encounters.flush();
        return view(actor, e);
    }

    /**
     * Firma: guarda lo que viene, valida y cierra la consulta. Ver el flujo
     * en el comentario de la clase.
     */
    @Transactional
    public ClinicalDtos.EncounterView sign(SaludAccess.Actor actor, Long id, ClinicalDtos.EncounterRequest req) {
        requireClinical(actor, EDITAR);
        Encounter e = find(id);
        apply(e, req);
        List<EncounterDiagnosis> dx = diagnoses.findByEncounterIdOrderByPosition(e.getId());
        ClinicalRules.checkSignable(e.getSubjective(), e.getAssessment(), dx.size(), e.getCaseId(), e.getNewCaseTitle());

        Instant ahora = Instant.now();
        LocalDate hoy = PatientRules.today();
        if (e.getCaseId() == null) {
            ClinicalCase c = new ClinicalCase();
            c.setCompanyId(e.getCompanyId());
            c.setPatientId(e.getPatientId());
            c.setTitle(e.getNewCaseTitle());
            c.setStatus(CASO_ABIERTO);
            c.setOpenedAt(ahora);
            c.setOpenedBy(actor.userId());
            cases.save(c);
            e.setCaseId(c.getId());
        }
        e.setNewCaseTitle(null);
        e.setStatus(FIRMADA);
        e.setSignedAt(ahora);
        e.setSignedBy(actor.userId());
        encounters.update(e);

        recetaAMedicamentos(e, hoy);
        if (e.getFollowUpDays() != null) {
            FollowUp f = new FollowUp();
            f.setCompanyId(e.getCompanyId());
            f.setPatientId(e.getPatientId());
            f.setCaseId(e.getCaseId());
            f.setEncounterId(e.getId());
            f.setProfessionalId(e.getProfessionalId());
            f.setTypeId(e.getFollowUpTypeId());
            f.setDueOn(ClinicalRules.dueOn(hoy, e.getFollowUpDays()));
            f.setNotes(e.getFollowUpNotes());
            f.setStatus(FU_PENDIENTE);
            f.setCreatedAt(ahora);
            followUps.save(f);
        }
        if (e.getAppointmentId() != null) {
            // El control que esta cita venía a cumplir queda cumplido.
            for (FollowUp f : followUps.findByAppointmentId(e.getAppointmentId())) {
                if (FU_AGENDADO.equals(f.getStatus())) {
                    f.setStatus(FU_CUMPLIDO);
                    f.setClosedAt(ahora);
                    followUps.update(f);
                }
            }
            appointments.findById(e.getAppointmentId()).ifPresent(a -> {
                if (AgendaRules.EN_ATENCION.equals(a.getStatus())) {
                    a.setStatus(AgendaRules.COMPLETADA);
                    a.setCompletedAt(ahora);
                    a.setStatusChangedBy(actor.userId());
                    if (a.getCaseId() == null) a.setCaseId(e.getCaseId());
                    appointments.update(a);
                }
            });
        }
        encounters.flush();
        log(actor, e.getPatientId(), LOG_FIRMAR);
        return view(actor, e);
    }

    /** Corrección a una consulta firmada: se agrega, no se reescribe. */
    @Transactional
    public ClinicalDtos.EncounterView addAddendum(SaludAccess.Actor actor, Long id, ClinicalDtos.AddendumRequest req) {
        requireClinical(actor, EDITAR);
        Encounter e = find(id);
        if (!FIRMADA.equals(e.getStatus())) {
            throw new IllegalStateException("La consulta todavía es borrador: edítala directamente.");
        }
        EncounterAddendum ad = new EncounterAddendum();
        ad.setEncounterId(e.getId());
        ad.setAuthorId(actor.userId());
        ad.setBody(req.text().trim());
        ad.setCreatedAt(Instant.now());
        addenda.save(ad);
        log(actor, e.getPatientId(), LOG_ADENDA);
        return view(actor, e);
    }

    // ── Historia, casos y seguimientos ───────────────────────────────────

    /** Casos del paciente con sus consultas y controles, y los borradores sin caso. */
    @Transactional
    public ClinicalDtos.ClinicalHistory history(SaludAccess.Actor actor, Long patientId) {
        requireClinical(actor, VER);
        Long companyId = tenant.getCompanyId();
        patientRows.findByIdAndCompanyId(patientId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente " + patientId + " no encontrado"));
        LocalDate hoy = PatientRules.today();
        List<EncounterRow> filas = encounterRows.findByPatientIdAndCompanyIdOrderByCreatedAtDesc(patientId, companyId);
        Map<Long, List<ClinicalDtos.EncounterSummary>> porCaso = new HashMap<>();
        List<ClinicalDtos.EncounterSummary> sinCaso = new ArrayList<>();
        for (EncounterRow r : filas) {
            if (r.getCaseId() == null) sinCaso.add(summary(r));
            else porCaso.computeIfAbsent(r.getCaseId(), k -> new ArrayList<>()).add(summary(r));
        }
        Map<Long, List<ClinicalDtos.FollowUpView>> controles = new HashMap<>();
        Map<Long, String> titulos = new HashMap<>();
        List<ClinicalCase> casos = cases.findByPatientIdAndCompanyIdOrderByOpenedAtDesc(patientId, companyId);
        casos.forEach(c -> titulos.put(c.getId(), c.getTitle()));
        for (FollowUp f : followUps.findByPatientIdAndCompanyIdOrderByDueOn(patientId, companyId)) {
            controles.computeIfAbsent(f.getCaseId(), k -> new ArrayList<>()).add(toView(f, titulos.get(f.getCaseId()), hoy));
        }
        // Abiertos primero; dentro de cada grupo, el más reciente arriba.
        List<ClinicalDtos.CaseView> out = casos.stream()
                .sorted((x, y) -> CASO_ABIERTO.equals(x.getStatus()) == CASO_ABIERTO.equals(y.getStatus())
                        ? y.getOpenedAt().compareTo(x.getOpenedAt())
                        : CASO_ABIERTO.equals(x.getStatus()) ? -1 : 1)
                .map(c -> new ClinicalDtos.CaseView(c.getId(), c.getTitle(), c.getStatus(), c.getOpenedAt(),
                        c.getClosedAt(), c.getOutcome(), porCaso.getOrDefault(c.getId(), List.of()),
                        controles.getOrDefault(c.getId(), List.of())))
                .toList();
        log(actor, patientId, LOG_HISTORIA);
        return new ClinicalDtos.ClinicalHistory(out, sinCaso);
    }

    /** Cierra el caso (resuelto) y cancela sus controles sin agendar. */
    @Transactional
    public ClinicalDtos.CaseView closeCase(SaludAccess.Actor actor, Long caseId, ClinicalDtos.CloseCaseRequest req) {
        requireClinical(actor, EDITAR);
        ClinicalCase c = findCase(caseId);
        if (CASO_CERRADO.equals(c.getStatus())) throw new IllegalStateException("El caso ya está cerrado.");
        Instant ahora = Instant.now();
        c.setStatus(CASO_CERRADO);
        c.setClosedAt(ahora);
        c.setClosedBy(actor.userId());
        c.setOutcome(blank(req.outcome()));
        cases.update(c);
        for (FollowUp f : followUps.findByCaseIdAndStatus(c.getId(), FU_PENDIENTE)) {
            f.setStatus(FU_CANCELADO);
            f.setClosedAt(ahora);
            followUps.update(f);
        }
        return caseView(c);
    }

    @Transactional
    public ClinicalDtos.CaseView reopenCase(SaludAccess.Actor actor, Long caseId) {
        requireClinical(actor, EDITAR);
        ClinicalCase c = findCase(caseId);
        if (CASO_ABIERTO.equals(c.getStatus())) throw new IllegalStateException("El caso ya está abierto.");
        c.setStatus(CASO_ABIERTO);
        c.setClosedAt(null);
        c.setClosedBy(null);
        cases.update(c);
        return caseView(c);
    }

    /** Un control que ya no hace falta. Si tenía cita, la cita sigue: se cancela en la agenda. */
    @Transactional
    public ClinicalDtos.FollowUpView cancelFollowUp(SaludAccess.Actor actor, Long id) {
        requireClinical(actor, EDITAR);
        FollowUp f = followUps.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Control " + id + " no encontrado"));
        if (!FU_PENDIENTE.equals(f.getStatus())) {
            throw new IllegalStateException("Solo se cancela un control pendiente (este está «" + f.getStatus() + "»).");
        }
        f.setStatus(FU_CANCELADO);
        f.setClosedAt(Instant.now());
        followUps.update(f);
        return toView(f, cases.findById(f.getCaseId()).map(ClinicalCase::getTitle).orElse(null), PatientRules.today());
    }

    @Transactional
    public List<ClinicalDtos.Cie10View> searchCie10(SaludAccess.Actor actor, @Nullable String q) {
        requireClinical(actor, VER);
        String texto = blank(q);
        if (texto == null || texto.length() < 2) return List.of();
        String codigo = texto.toUpperCase(Locale.ROOT).replace(" ", "") + "%";
        return cie10.search(ClinicalRules.likePattern(texto), codigo, ClinicalRules.MAX_CIE10).stream()
                .map(c -> new ClinicalDtos.Cie10View(c.getCode(), c.getDescription()))
                .toList();
    }

    // ── Para la receta en PDF ────────────────────────────────────────────

    /** La consulta firmada con lo que imprime la receta. Queda en la bitácora. */
    @Transactional
    public ClinicalDtos.EncounterView forPrescription(SaludAccess.Actor actor, Long id) {
        requireClinical(actor, VER);
        Encounter e = find(id);
        if (!FIRMADA.equals(e.getStatus())) throw new IllegalStateException("La receta se imprime con la consulta firmada.");
        if (prescriptions.findByEncounterIdOrderByPosition(e.getId()).isEmpty()) {
            throw new IllegalStateException("Esta consulta no tiene receta.");
        }
        log(actor, e.getPatientId(), LOG_RECETA);
        return view(actor, e);
    }

    // ── Internos ─────────────────────────────────────────────────────────

    /** El borrador con lo que viene: texto, signos, listas completas y plan. */
    private void apply(Encounter e, ClinicalDtos.EncounterRequest req) {
        ClinicalRules.checkEditable(e.getStatus());
        ClinicalRules.checkVitals(req.vitals());
        List<ClinicalDtos.DiagnosisData> dx = ClinicalRules.diagnoses(req.diagnoses());
        List<ClinicalDtos.PrescriptionData> rx = ClinicalRules.prescriptions(req.prescriptions());

        e.setCaseId(req.caseId() == null ? null : openCase(req.caseId(), e.getPatientId()).getId());
        e.setNewCaseTitle(req.caseId() == null ? blank(req.newCaseTitle()) : null);
        e.setSubjective(blank(req.subjective()));
        e.setObjective(blank(req.objective()));
        e.setAssessment(blank(req.assessment()));
        e.setPlan(blank(req.plan()));
        e.setInstructions(blank(req.instructions()));
        ClinicalDtos.Vitals v = req.vitals();
        e.setBpSystolic(v == null ? null : v.bpSystolic());
        e.setBpDiastolic(v == null ? null : v.bpDiastolic());
        e.setHeartRate(v == null ? null : v.heartRate());
        e.setRespRate(v == null ? null : v.respRate());
        e.setTemperature(v == null ? null : v.temperature());
        e.setSpo2(v == null ? null : v.spo2());
        e.setWeightKg(v == null ? null : v.weightKg());
        e.setHeightCm(v == null ? null : v.heightCm());
        e.setPainScore(v == null ? null : v.painScore());
        e.setResponse(ClinicalRules.response(req.response()));
        e.setAdherence(ClinicalRules.adherence(req.adherence()));
        e.setAdverseEffects(blank(req.adverseEffects()));
        e.setFollowUpDays(ClinicalRules.followUpDays(req.followUpDays()));
        if (req.followUpTypeId() != null) {
            types.findByIdAndCompanyId(req.followUpTypeId(), e.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de cita " + req.followUpTypeId() + " no encontrado"));
        }
        e.setFollowUpTypeId(e.getFollowUpDays() == null ? null : req.followUpTypeId());
        e.setFollowUpNotes(e.getFollowUpDays() == null ? null : blank(req.followUpNotes()));
        e.setUpdatedAt(Instant.now());

        diagnoses.deleteByEncounterId(e.getId());
        for (int i = 0; i < dx.size(); i++) {
            ClinicalDtos.DiagnosisData d = dx.get(i);
            EncounterDiagnosis x = new EncounterDiagnosis();
            x.setEncounterId(e.getId());
            x.setCode(d.code());
            x.setDescription(d.description());
            x.setPrimary(Boolean.TRUE.equals(d.primary()));
            x.setPosition(i);
            diagnoses.save(x);
        }
        prescriptions.deleteByEncounterId(e.getId());
        for (int i = 0; i < rx.size(); i++) {
            ClinicalDtos.PrescriptionData p = rx.get(i);
            PrescriptionItem x = new PrescriptionItem();
            x.setEncounterId(e.getId());
            x.setMedication(p.medication());
            x.setDose(p.dose());
            x.setRoute(p.route());
            x.setFrequency(p.frequency());
            x.setDurationDays(p.durationDays());
            x.setQuantity(p.quantity());
            x.setInstructions(p.instructions());
            x.setPosition(i);
            prescriptions.save(x);
        }
    }

    /**
     * Lo recetado pasa a «medicamentos de uso actual»: si ya estaba (mismo
     * nombre), se actualizan dosis y frecuencia; si no, se agrega. Quitar los
     * que terminó sigue siendo manual, en los datos clínicos.
     */
    private void recetaAMedicamentos(Encounter e, LocalDate hoy) {
        List<PrescriptionItem> rx = prescriptions.findByEncounterIdOrderByPosition(e.getId());
        if (rx.isEmpty()) return;
        Map<String, PatientMedication> actuales = new LinkedHashMap<>();
        for (PatientMedication m : medications.findByPatientIdOrderById(e.getPatientId())) {
            actuales.putIfAbsent(ClinicalRules.normalize(m.getName()), m);
        }
        for (PrescriptionItem p : rx) {
            PatientMedication m = actuales.get(ClinicalRules.normalize(p.getMedication()));
            boolean nuevo = m == null;
            if (nuevo) {
                m = new PatientMedication();
                m.setPatientId(e.getPatientId());
                m.setCompanyId(e.getCompanyId());
                m.setName(p.getMedication());
            }
            m.setDose(p.getDose());
            m.setFrequency(p.getFrequency());
            m.setSince(ClinicalRules.since(hoy));
            m.setNotes(p.getDurationDays() != null
                    ? "Recetado por " + p.getDurationDays() + " días"
                    : "Recetado en consulta");
            if (nuevo) medications.save(m); else medications.update(m);
        }
    }

    private ClinicalDtos.EncounterView view(SaludAccess.Actor actor, Encounter e) {
        PatientRow paciente = patientRows.findByIdAndCompanyId(e.getPatientId(), e.getCompanyId()).orElse(null);
        EncounterRow fila = encounterRows.findById(e.getId()).orElse(null);
        Appointment cita = e.getAppointmentId() == null ? null : appointments.findById(e.getAppointmentId()).orElse(null);
        ClinicalCase caso = e.getCaseId() == null ? null : cases.findById(e.getCaseId()).orElse(null);
        Map<Long, String> nombres = new HashMap<>();
        List<EncounterAddendum> notas = addenda.findByEncounterIdOrderByCreatedAt(e.getId());
        if (!notas.isEmpty()) {
            for (UserDtos.Response u : users.list()) nombres.put(u.id(), u.name());
        }
        EncounterRow previa = e.getCaseId() == null ? null
                : encounterRows.previousSigned(e.getCaseId(), e.getId()).orElse(null);
        List<ClinicalDtos.CaseOption> abiertos = cases.findByPatientIdAndCompanyIdOrderByOpenedAtDesc(e.getPatientId(), e.getCompanyId())
                .stream().filter(c -> CASO_ABIERTO.equals(c.getStatus()))
                .map(c -> new ClinicalDtos.CaseOption(c.getId(), c.getTitle(), c.getOpenedAt()))
                .toList();
        List<String> alergias = allergies.findByPatientIdOrderById(e.getPatientId()).stream()
                .map(al -> al.getAllergen() + (al.getSeverity() != null ? " (" + al.getSeverity() + ")" : ""))
                .toList();
        return new ClinicalDtos.EncounterView(
                e.getId(), e.getPatientId(),
                paciente != null ? paciente.getName() : "",
                paciente != null ? paciente.getRecordNumber() : null,
                paciente != null ? PatientRules.age(paciente.getBirthDate(), PatientRules.today()) : null,
                paciente != null ? paciente.getSex() : null,
                e.getAppointmentId(),
                cita != null ? cita.getStartsAt() : null,
                cita != null ? cita.getReason() : null,
                e.getProfessionalId(),
                fila != null ? fila.getProfessionalName() : "",
                e.getCaseId(), caso != null ? caso.getTitle() : null, e.getNewCaseTitle(),
                e.getStatus(),
                e.getSubjective(), e.getObjective(), e.getAssessment(), e.getPlan(), e.getInstructions(),
                vitals(e.getBpSystolic(), e.getBpDiastolic(), e.getHeartRate(), e.getRespRate(), e.getTemperature(),
                        e.getSpo2(), e.getWeightKg(), e.getHeightCm(), e.getPainScore()),
                e.getResponse(), e.getAdherence(), e.getAdverseEffects(),
                diagnoses.findByEncounterIdOrderByPosition(e.getId()).stream()
                        .map(d -> new ClinicalDtos.DiagnosisView(d.getCode(), d.getDescription(), d.isPrimary())).toList(),
                prescriptions.findByEncounterIdOrderByPosition(e.getId()).stream()
                        .map(p -> new ClinicalDtos.PrescriptionView(p.getMedication(), p.getDose(), p.getRoute(),
                                p.getFrequency(), p.getDurationDays(), p.getQuantity(), p.getInstructions())).toList(),
                notas.stream().map(n -> new ClinicalDtos.AddendumView(n.getId(), n.getAuthorId(),
                        nombres.get(n.getAuthorId()), n.getBody(), n.getCreatedAt())).toList(),
                e.getFollowUpDays(), e.getFollowUpTypeId(), e.getFollowUpNotes(),
                e.getCreatedAt(), e.getSignedAt(),
                BORRADOR.equals(e.getStatus()) && actor.can(CLINICAL, EDITAR),
                previa == null ? null : summary(previa),
                abiertos,
                alergias);
    }

    static ClinicalDtos.EncounterSummary summary(EncounterRow r) {
        return new ClinicalDtos.EncounterSummary(r.getId(), r.getAppointmentId(), r.getStatus(), r.getCreatedAt(),
                r.getSignedAt(), r.getProfessionalId(), r.getProfessionalName(), r.getCaseId(), r.getCaseTitle(),
                r.getDxCode(), r.getDxDescription(), r.getResponse(),
                r.getRxCount() == null ? 0 : r.getRxCount(),
                vitals(r.getBpSystolic(), r.getBpDiastolic(), r.getHeartRate(), null, r.getTemperature(),
                        r.getSpo2(), r.getWeightKg(), r.getHeightCm(), r.getPainScore()));
    }

    static ClinicalDtos.VitalsView vitals(@Nullable Integer sis, @Nullable Integer dia, @Nullable Integer fc,
                                          @Nullable Integer fr, @Nullable java.math.BigDecimal temp, @Nullable Integer spo2,
                                          @Nullable java.math.BigDecimal peso, @Nullable java.math.BigDecimal talla,
                                          @Nullable Integer dolor) {
        return new ClinicalDtos.VitalsView(sis, dia, fc, fr, temp, spo2, peso, talla, dolor, ClinicalRules.bmi(peso, talla));
    }

    static ClinicalDtos.FollowUpView toView(FollowUp f, @Nullable String caseTitle, LocalDate hoy) {
        return new ClinicalDtos.FollowUpView(f.getId(), f.getCaseId(), caseTitle, f.getDueOn(), f.getTypeId(),
                f.getProfessionalId(), f.getNotes(), f.getStatus(), f.getAppointmentId(),
                ChronoUnit.DAYS.between(hoy, f.getDueOn()), f.getReminderStatus(), f.getReminderSentAt(),
                f.getFeeling(), f.getFeelingAt(), f.getFeelingNote());
    }

    private ClinicalDtos.CaseView caseView(ClinicalCase c) {
        LocalDate hoy = PatientRules.today();
        List<ClinicalDtos.EncounterSummary> consultas = encounterRows
                .findByPatientIdAndCompanyIdOrderByCreatedAtDesc(c.getPatientId(), c.getCompanyId()).stream()
                .filter(r -> Objects.equals(r.getCaseId(), c.getId())).map(ClinicalService::summary).toList();
        List<ClinicalDtos.FollowUpView> controles = followUps
                .findByPatientIdAndCompanyIdOrderByDueOn(c.getPatientId(), c.getCompanyId()).stream()
                .filter(f -> Objects.equals(f.getCaseId(), c.getId())).map(f -> toView(f, c.getTitle(), hoy)).toList();
        return new ClinicalDtos.CaseView(c.getId(), c.getTitle(), c.getStatus(), c.getOpenedAt(), c.getClosedAt(),
                c.getOutcome(), consultas, controles);
    }

    private Encounter find(Long id) {
        return encounters.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Consulta " + id + " no encontrada"));
    }

    private ClinicalCase findCase(Long id) {
        return cases.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Caso " + id + " no encontrado"));
    }

    /** Un caso abierto del mismo paciente; si no, error claro. */
    private ClinicalCase openCase(Long caseId, Long patientId) {
        ClinicalCase c = findCase(caseId);
        if (!c.getPatientId().equals(patientId)) throw new IllegalStateException("Ese caso es de otro paciente.");
        if (!CASO_ABIERTO.equals(c.getStatus())) throw new IllegalStateException("El caso «" + c.getTitle() + "» está cerrado: reábrelo o crea uno nuevo.");
        return c;
    }

    /** El caso de la cita de control, si sigue abierto; si no, la consulta empieza sin caso. */
    private @Nullable Long openCaseOrNull(@Nullable Long caseId, Long patientId) {
        if (caseId == null) return null;
        return cases.findByIdAndCompanyId(caseId, tenant.getCompanyId())
                .filter(c -> c.getPatientId().equals(patientId) && CASO_ABIERTO.equals(c.getStatus()))
                .map(ClinicalCase::getId).orElse(null);
    }

    /** Expediente clínico, y nunca el asistente: su token no debe llegar aquí aunque tuviera el permiso. */
    private static void requireClinical(SaludAccess.Actor actor, String action) {
        if (actor.service()) throw new SaludForbiddenException("El asistente no tiene acceso al expediente clínico.");
        actor.require(CLINICAL, action);
    }

    private void log(SaludAccess.Actor actor, Long patientId, String action) {
        AccessLog l = new AccessLog();
        l.setCompanyId(tenant.getCompanyId());
        l.setPatientId(patientId);
        l.setUserId(actor.userId());
        l.setAction(action);
        l.setOccurredAt(Instant.now());
        accessLog.save(l);
    }
}
