package com.erp_maya.salud.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.salud.domain.Appointment;
import com.erp_maya.salud.domain.AppointmentType;
import com.erp_maya.salud.domain.ClinicalCase;
import com.erp_maya.salud.domain.TreatmentPlan;
import com.erp_maya.salud.dto.CajaDtos;
import com.erp_maya.salud.repository.AppointmentRepository;
import com.erp_maya.salud.repository.AppointmentTypeRepository;
import com.erp_maya.salud.repository.ClinicalCaseRepository;
import com.erp_maya.salud.repository.ProfessionalRepository;
import com.erp_maya.salud.repository.TreatmentPlanRepository;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.UserRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.erp_maya.salud.service.SaludAccess.AGENDA;
import static com.erp_maya.salud.service.SaludAccess.CLINICAL;
import static com.erp_maya.salud.service.SaludAccess.EDITAR;
import static com.erp_maya.salud.service.SaludAccess.VER;

/**
 * Planes de tratamiento: «10 sesiones de fisioterapia, cada 3 días» dentro
 * de un caso. Los indica el profesional (clinical_record); recepción los ve
 * desde la agenda para agendar la siguiente sesión, que queda en el plan. Al
 * completarse la última sesión el plan se cierra solo.
 */
@Singleton
public class TreatmentPlanService {

    private final TreatmentPlanRepository plans;
    private final ClinicalCaseRepository cases;
    private final AppointmentRepository appointments;
    private final AppointmentTypeRepository types;
    private final ProfessionalRepository professionals;
    private final UserRepository users;
    private final TenantContext tenant;

    public TreatmentPlanService(TreatmentPlanRepository plans, ClinicalCaseRepository cases,
                                AppointmentRepository appointments, AppointmentTypeRepository types,
                                ProfessionalRepository professionals, UserRepository users, TenantContext tenant) {
        this.plans = plans;
        this.cases = cases;
        this.appointments = appointments;
        this.types = types;
        this.professionals = professionals;
        this.users = users;
        this.tenant = tenant;
    }

    /** Los planes del paciente, activos primero. Los ve el expediente y la agenda (para agendar sesiones). */
    @Transactional
    public List<CajaDtos.PlanView> forPatient(SaludAccess.Actor actor, Long patientId) {
        if (!actor.can(CLINICAL, VER)) actor.require(AGENDA, VER);
        LocalDate hoy = PatientRules.today();
        return plans.findByPatientIdAndCompanyIdOrderByCreatedAtDesc(patientId, tenant.getCompanyId()).stream()
                .sorted((x, y) -> Boolean.compare(!CajaRules.PLAN_ACTIVO.equals(x.getStatus()), !CajaRules.PLAN_ACTIVO.equals(y.getStatus())))
                .map(p -> view(p, hoy)).toList();
    }

    @Transactional
    public CajaDtos.PlanView create(SaludAccess.Actor actor, Long caseId, CajaDtos.PlanRequest req) {
        requireClinical(actor);
        Long companyId = tenant.getCompanyId();
        ClinicalCase c = cases.findByIdAndCompanyId(caseId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Caso " + caseId + " no encontrado"));
        if (!ClinicalRules.CASO_ABIERTO.equals(c.getStatus())) {
            throw new IllegalStateException("El caso está cerrado: reábrelo para indicar un plan.");
        }
        AppointmentType tipo = types.findByIdAndCompanyId(req.typeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de cita " + req.typeId() + " no encontrado"));
        if (!tipo.isActive()) throw new IllegalStateException("El tipo «" + tipo.getName() + "» está desactivado.");
        if (req.professionalId() != null) {
            professionals.findByUserIdAndCompanyId(req.professionalId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profesional " + req.professionalId() + " no encontrado"));
        }
        TreatmentPlan p = new TreatmentPlan();
        p.setCompanyId(companyId);
        p.setPatientId(c.getPatientId());
        p.setCaseId(c.getId());
        p.setTitle(req.title().trim());
        p.setTypeId(tipo.getId());
        p.setProfessionalId(req.professionalId());
        p.setSessions(CajaRules.sesionesPlan(req.sessions()));
        p.setIntervalDays(CajaRules.intervalo(req.intervalDays()));
        p.setNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
        p.setStatus(CajaRules.PLAN_ACTIVO);
        p.setCreatedBy(actor.userId());
        p.setCreatedAt(Instant.now());
        return view(plans.save(p), PatientRules.today());
    }

    @Transactional
    public CajaDtos.PlanView cancel(SaludAccess.Actor actor, Long id) {
        requireClinical(actor);
        TreatmentPlan p = find(id);
        if (!CajaRules.PLAN_ACTIVO.equals(p.getStatus())) throw new IllegalStateException("El plan ya está «" + p.getStatus() + "».");
        p.setStatus(CajaRules.PLAN_CANCELADO);
        p.setClosedAt(Instant.now());
        return view(plans.update(p), PatientRules.today());
    }

    /** Para agendar una sesión: el plan activo de ese paciente. */
    @Transactional
    public TreatmentPlan forAppointment(Long planId, Long patientId) {
        TreatmentPlan p = find(planId);
        if (!p.getPatientId().equals(patientId)) throw new IllegalStateException("Ese plan es de otro paciente.");
        if (!CajaRules.PLAN_ACTIVO.equals(p.getStatus())) throw new IllegalStateException("El plan ya está «" + p.getStatus() + "».");
        return p;
    }

    /** Tras completar una cita del plan: si ya se hicieron todas las sesiones, el plan queda completado. */
    @Transactional
    public void afterCompleted(Appointment a) {
        if (a.getPlanId() == null) return;
        plans.findById(a.getPlanId()).ifPresent(p -> {
            if (!CajaRules.PLAN_ACTIVO.equals(p.getStatus())) return;
            CajaRules.Avance av = CajaRules.avance(appointments.findByPlanId(p.getId()), p.getSessions(),
                    p.getIntervalDays(), PatientRules.today());
            if (av.hechas() >= p.getSessions()) {
                p.setStatus(CajaRules.PLAN_COMPLETADO);
                p.setClosedAt(Instant.now());
                plans.update(p);
            }
        });
    }

    private CajaDtos.PlanView view(TreatmentPlan p, LocalDate hoy) {
        CajaRules.Avance av = CajaRules.avance(appointments.findByPlanId(p.getId()), p.getSessions(), p.getIntervalDays(), hoy);
        String tipo = types.findByIdAndCompanyId(p.getTypeId(), p.getCompanyId()).map(AppointmentType::getName).orElse(null);
        String profesional = p.getProfessionalId() == null ? null
                : users.findByIdAndCompanyId(p.getProfessionalId(), p.getCompanyId()).map(User::getName).orElse(null);
        boolean activo = CajaRules.PLAN_ACTIVO.equals(p.getStatus());
        return new CajaDtos.PlanView(p.getId(), p.getPatientId(), p.getCaseId(), p.getTitle(), p.getTypeId(), tipo,
                p.getProfessionalId(), profesional, p.getSessions(), av.hechas(), av.agendadas(), activo ? av.faltan() : 0,
                p.getIntervalDays(), p.getNotes(), p.getStatus(), p.getCreatedAt(), p.getClosedAt(),
                activo ? av.siguiente() : null);
    }

    private TreatmentPlan find(Long id) {
        return plans.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan " + id + " no encontrado"));
    }

    private static void requireClinical(SaludAccess.Actor actor) {
        if (actor.service()) throw new SaludForbiddenException("El asistente no puede indicar planes de tratamiento.");
        actor.require(CLINICAL, EDITAR);
    }
}
