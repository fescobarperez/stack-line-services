package com.erp_maya.salud.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.salud.domain.Appointment;
import com.erp_maya.salud.domain.AppointmentRow;
import com.erp_maya.salud.domain.AppointmentType;
import com.erp_maya.salud.domain.Block;
import com.erp_maya.salud.domain.FollowUp;
import com.erp_maya.salud.domain.TreatmentPlan;
import com.erp_maya.salud.domain.Patient;
import com.erp_maya.salud.domain.Professional;
import com.erp_maya.salud.domain.Resource;
import com.erp_maya.salud.domain.Schedule;
import com.erp_maya.salud.dto.AgendaDtos;
import com.erp_maya.salud.repository.AppointmentRepository;
import com.erp_maya.salud.repository.AppointmentRowRepository;
import com.erp_maya.salud.repository.AppointmentTypeRepository;
import com.erp_maya.salud.repository.BlockRepository;
import com.erp_maya.salud.repository.FollowUpRepository;
import com.erp_maya.salud.repository.PatientRepository;
import com.erp_maya.salud.repository.ProfessionalRepository;
import com.erp_maya.salud.repository.ResourceRepository;
import com.erp_maya.salud.repository.ScheduleRepository;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.erp_maya.salud.service.AgendaRules.CANCELADA;
import static com.erp_maya.salud.service.AgendaRules.COMPLETADA;
import static com.erp_maya.salud.service.AgendaRules.CONFIRMADA;
import static com.erp_maya.salud.service.AgendaRules.EN_ATENCION;
import static com.erp_maya.salud.service.AgendaRules.EN_SALA;
import static com.erp_maya.salud.service.AgendaRules.NO_ASISTIO;
import static com.erp_maya.salud.service.AgendaRules.SOLICITADA;
import static com.erp_maya.salud.service.PatientRules.blank;
import static com.erp_maya.salud.service.SaludAccess.AGENDA;
import static com.erp_maya.salud.service.SaludAccess.CREAR;
import static com.erp_maya.salud.service.SaludAccess.EDITAR;
import static com.erp_maya.salud.service.SaludAccess.VER;

/**
 * Citas, bloqueos y disponibilidad.
 *
 * El no solape lo garantiza la base (EXCLUDE en 090_salud_agenda.sql). Aquí
 * se revisa antes solo para dar un mensaje que diga con qué se cruza; si dos
 * personas reservan el mismo horario a la vez, la verificación no lo ve y la
 * base rechaza a la segunda, que recibe el mismo 409.
 *
 * Todos los métodos públicos son puntos de entrada: no se llaman entre sí
 * (una llamada interna no pasa por el proxy de @Transactional).
 */
@Singleton
public class AppointmentService {

    /** Rango máximo de una consulta de agenda, para no traer años de citas de golpe. */
    static final int MAX_DIAS_RANGO = 62;
    static final int MAX_FILAS = 2000;
    static final int PASO_DISPONIBILIDAD = 15;
    /** Sin -1 en vez de null: ver BlockRepository. */
    private static final long NINGUNO = -1L;

    private final AppointmentRepository appointments;
    private final AppointmentRowRepository rows;
    private final BlockRepository blocks;
    private final ProfessionalRepository professionals;
    private final ScheduleRepository schedules;
    private final ResourceRepository resources;
    private final AppointmentTypeRepository types;
    private final PatientRepository patients;
    private final FollowUpRepository followUps;
    private final TreatmentPlanService plans;
    private final SaludBillingService billing;
    private final TenantContext tenant;

    public AppointmentService(AppointmentRepository appointments, AppointmentRowRepository rows,
                              BlockRepository blocks, ProfessionalRepository professionals,
                              ScheduleRepository schedules, ResourceRepository resources,
                              AppointmentTypeRepository types, PatientRepository patients,
                              FollowUpRepository followUps, TreatmentPlanService plans,
                              SaludBillingService billing, TenantContext tenant) {
        this.appointments = appointments;
        this.rows = rows;
        this.blocks = blocks;
        this.professionals = professionals;
        this.schedules = schedules;
        this.resources = resources;
        this.types = types;
        this.patients = patients;
        this.followUps = followUps;
        this.plans = plans;
        this.billing = billing;
        this.tenant = tenant;
    }

    // ── Consultas ────────────────────────────────────────────────────────

    /**
     * Citas que tocan [from, to). Con `patientId` el rango puede ser largo
     * (historial del paciente); sin él, hasta 62 días.
     */
    @Transactional
    public List<AgendaDtos.AppointmentView> list(SaludAccess.Actor actor, Instant from, Instant to,
                                                 @Nullable Long professionalId, @Nullable Long resourceId,
                                                 @Nullable Long patientId, @Nullable String status) {
        actor.require(AGENDA, VER);
        if (!to.isAfter(from)) throw new IllegalStateException("El rango de fechas está invertido.");
        if (patientId == null && Duration.between(from, to).toDays() > MAX_DIAS_RANGO) {
            throw new IllegalStateException("Consulta como máximo " + MAX_DIAS_RANGO + " días de agenda a la vez.");
        }
        String estado = status == null || status.isBlank() ? "" : PatientRules.oneOf(status, AgendaRules.STATUSES, "Estado");
        return rows.search(tenant.getCompanyId(), from, to, nz(professionalId), nz(resourceId), nz(patientId),
                        estado, MAX_FILAS)
                .stream().map(AppointmentService::toView).toList();
    }

    @Transactional
    public AgendaDtos.AppointmentView get(SaludAccess.Actor actor, Long id) {
        actor.require(AGENDA, VER);
        return view(id);
    }

    /** Horarios libres de un profesional para un tipo de cita en un día. */
    @Transactional
    public AgendaDtos.Availability availability(SaludAccess.Actor actor, Long professionalId, LocalDate date,
                                                Long typeId, @Nullable Integer step) {
        actor.require(AGENDA, VER);
        Long companyId = tenant.getCompanyId();
        Professional prof = activeProfessional(professionalId);
        AppointmentType tipo = activeType(typeId);
        Instant[] dia = AgendaRules.day(date);

        List<AgendaRules.Window> ventanas = new ArrayList<>();
        for (Schedule s : schedules.findByProfessionalIdAndWeekday(prof.getUserId(), date.getDayOfWeek().getValue())) {
            ventanas.add(new AgendaRules.Window(s.getStartTime(), s.getEndTime(), s.getResourceId()));
        }
        List<AgendaRules.Busy> ocupadoProf = new ArrayList<>();
        Map<Long, List<AgendaRules.Busy>> ocupadoRec = new HashMap<>();
        for (Appointment a : appointments.activeInRange(companyId, dia[0], dia[1])) {
            AgendaRules.Busy b = new AgendaRules.Busy(a.getStartsAt(), a.getEndsAt());
            if (a.getProfessionalId().equals(prof.getUserId())) ocupadoProf.add(b);
            if (a.getResourceId() != null) ocupadoRec.computeIfAbsent(a.getResourceId(), k -> new ArrayList<>()).add(b);
        }
        for (Block bl : blocks.inRange(companyId, dia[0], dia[1])) {
            AgendaRules.Busy b = new AgendaRules.Busy(bl.getStartsAt(), bl.getEndsAt());
            if (prof.getUserId().equals(bl.getProfessionalId())) ocupadoProf.add(b);
            if (bl.getResourceId() != null) ocupadoRec.computeIfAbsent(bl.getResourceId(), k -> new ArrayList<>()).add(b);
        }
        List<Long> recursos = resources.findByCompanyIdAndActiveOrderByName(companyId, true).stream()
                .map(Resource::getId).toList();
        int paso = step != null ? step : PASO_DISPONIBILIDAD;
        List<AgendaDtos.Slot> libres = AgendaRules.slots(date, ventanas, ocupadoProf, ocupadoRec, recursos,
                tipo.isRequiresResource(), tipo.getDurationMinutes(), paso, Instant.now());
        return new AgendaDtos.Availability(date, prof.getUserId(), tipo.getId(), tipo.getDurationMinutes(), libres);
    }

    /** Notas y bloqueos son de la clínica: el asistente no los toca aunque su token pueda editar citas. */
    private static void soloPersonal(SaludAccess.Actor actor) {
        if (actor.service()) {
            throw new SaludForbiddenException("El asistente no puede modificar notas ni bloqueos de la agenda.");
        }
    }

    // ── Citas ────────────────────────────────────────────────────────────

    @Transactional
    public AgendaDtos.AppointmentView create(SaludAccess.Actor actor, AgendaDtos.AppointmentRequest req) {
        actor.require(AGENDA, CREAR);
        Long companyId = tenant.getCompanyId();
        Patient paciente = patients.findByIdAndCompanyId(req.patientId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente " + req.patientId() + " no encontrado"));
        if (!"activo".equals(paciente.getStatus())) {
            throw new IllegalStateException("El paciente está «" + paciente.getStatus() + "»: no se le pueden agendar citas.");
        }
        // El asistente siempre deja la cita «solicitada»: la confirma la clínica o el paciente con el recordatorio.
        String estado = actor.service() ? SOLICITADA
                : req.status() == null || req.status().isBlank() ? CONFIRMADA : req.status().trim();
        if (!SOLICITADA.equals(estado) && !CONFIRMADA.equals(estado)) {
            throw new IllegalStateException("Una cita nueva empieza como «solicitada» o «confirmada».");
        }
        // Agendar un control pendiente: la cita queda en su caso y el control, agendado.
        FollowUp control = req.followUpId() == null ? null : pendingFollowUp(req.followUpId(), paciente.getId());
        // Sesión de un plan de tratamiento: la cita queda en el plan y en su caso.
        TreatmentPlan plan = req.planId() == null ? null : plans.forAppointment(req.planId(), paciente.getId());
        Professional prof = activeProfessional(req.professionalId());
        AppointmentType tipo = activeType(req.typeId());
        Long recurso = resourceFor(req.resourceId(), tipo);
        Instant ahora = Instant.now();
        AgendaRules.checkNotPast(req.startsAt(), ahora, AgendaRules.TOLERANCIA_PASADO);
        Instant fin = AgendaRules.end(req.startsAt(), req.endsAt(), tipo.getDurationMinutes());
        if (actor.service()) checkSchedule(prof.getUserId(), req.startsAt(), fin);
        checkFree(prof.getUserId(), recurso, req.startsAt(), fin, null);

        Appointment a = new Appointment();
        a.setCompanyId(companyId);
        a.setPatientId(paciente.getId());
        a.setProfessionalId(prof.getUserId());
        a.setResourceId(recurso);
        a.setTypeId(tipo.getId());
        a.setStartsAt(req.startsAt());
        a.setEndsAt(fin);
        a.setStatus(estado);
        a.setSource(actor.service() ? "tino" : "recepcion");
        a.setReason(blank(req.reason()));
        a.setNotes(blank(req.notes()));
        a.setCreatedBy(actor.userId());
        a.setCreatedAt(ahora);
        a.setStatusChangedBy(actor.userId());
        if (CONFIRMADA.equals(estado)) a.setConfirmedAt(ahora);
        if (control != null) a.setCaseId(control.getCaseId());
        if (plan != null) {
            a.setPlanId(plan.getId());
            if (a.getCaseId() == null) a.setCaseId(plan.getCaseId());
        }
        guardar(() -> appointments.save(a));
        if (control != null) {
            control.setStatus(ClinicalRules.FU_AGENDADO);
            control.setAppointmentId(a.getId());
            followUps.update(control);
        }
        return view(a.getId());
    }

    /** Mueve la cita de hora (y de profesional o recurso, si se piden). */
    @Transactional
    public AgendaDtos.AppointmentView reschedule(SaludAccess.Actor actor, Long id, AgendaDtos.RescheduleRequest req) {
        actor.require(AGENDA, EDITAR);
        Appointment a = find(id);
        AgendaRules.checkReschedulable(a.getStatus());
        Professional prof = activeProfessional(req.professionalId() != null ? req.professionalId() : a.getProfessionalId());
        AppointmentType tipo = types.findByIdAndCompanyId(a.getTypeId(), a.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de cita " + a.getTypeId() + " no encontrado"));
        Long recurso = resourceFor(req.resourceId() != null ? req.resourceId() : a.getResourceId(), tipo);
        Instant ahora = Instant.now();
        AgendaRules.checkNotPast(req.startsAt(), ahora, Duration.ZERO);
        // Sin fin explícito se conserva la duración que tenía la cita (pudo ser distinta a la del tipo).
        Instant fin = req.endsAt() != null ? AgendaRules.end(req.startsAt(), req.endsAt(), tipo.getDurationMinutes())
                : req.startsAt().plus(Duration.between(a.getStartsAt(), a.getEndsAt()));
        if (actor.service()) checkSchedule(prof.getUserId(), req.startsAt(), fin);
        checkFree(prof.getUserId(), recurso, req.startsAt(), fin, a.getId());

        a.setProfessionalId(prof.getUserId());
        a.setResourceId(recurso);
        a.setStartsAt(req.startsAt());
        a.setEndsAt(fin);
        // Movida por el asistente vuelve a «solicitada»; por recepción, sigue confirmada salvo que se pida otra cosa.
        boolean confirmada = !actor.service() && (req.confirmed() == null || req.confirmed());
        a.setStatus(confirmada ? CONFIRMADA : SOLICITADA);
        a.setConfirmedAt(confirmada ? ahora : null);
        a.setStatusChangedBy(actor.userId());
        // El recordatorio era de la hora anterior: se vuelve a mandar para la nueva.
        a.setReminderStatus(null);
        a.setReminderAttempts(0);
        a.setReminderNextAt(null);
        a.setReminderSentAt(null);
        a.setReminderError(null);
        guardar(() -> appointments.update(a));
        return view(a.getId());
    }

    /** Cambio de estado según el diagrama (AgendaRules.TRANSITIONS). */
    @Transactional
    public AgendaDtos.AppointmentView changeStatus(SaludAccess.Actor actor, Long id, AgendaDtos.StatusRequest req) {
        actor.require(AGENDA, EDITAR);
        Appointment a = find(id);
        String destino = req.status().trim();
        if (actor.service() && !AgendaRules.CAMBIOS_DEL_ASISTENTE.contains(destino)) {
            throw new IllegalStateException("El asistente solo puede confirmar o cancelar citas.");
        }
        AgendaRules.checkTransition(a.getStatus(), destino);
        Instant ahora = Instant.now();
        AgendaRules.checkTiming(destino, a.getStartsAt(), ahora);
        switch (destino) {
            case CONFIRMADA -> a.setConfirmedAt(ahora);
            case EN_SALA -> {
                a.setArrivedAt(ahora);
                if (a.getConfirmedAt() == null) a.setConfirmedAt(ahora);
            }
            case EN_ATENCION -> a.setStartedAt(ahora);
            case COMPLETADA -> a.setCompletedAt(ahora);
            case CANCELADA -> {
                String motivo = blank(req.reason());
                if (motivo == null) throw new IllegalStateException("Indica el motivo de la cancelación.");
                a.setCancelReason(motivo);
                a.setCancelledAt(ahora);
            }
            default -> { /* no_asistio: solo el estado */ }
        }
        a.setStatus(destino);
        a.setStatusChangedBy(actor.userId());
        appointments.update(a);
        if (CANCELADA.equals(destino) || NO_ASISTIO.equals(destino)) liberarControles(a.getId());
        if (COMPLETADA.equals(destino)) {
            // Fase 7: el paquete del paciente descuenta la sesión y el plan avanza (o se completa).
            billing.autoPackage(actor, a);
            plans.afterCompleted(a);
        }
        // La vista salud_v_appointments no está entre las tablas que Hibernate
        // sabe que cambiaron: sin el flush, la respuesta saldría con el estado viejo.
        appointments.flush();
        return view(a.getId());
    }

    @Transactional
    public AgendaDtos.AppointmentView updateNotes(SaludAccess.Actor actor, Long id, AgendaDtos.NotesRequest req) {
        actor.require(AGENDA, EDITAR);
        soloPersonal(actor);
        Appointment a = find(id);
        a.setReason(blank(req.reason()));
        a.setNotes(blank(req.notes()));
        appointments.update(a);
        appointments.flush();
        return view(a.getId());
    }

    // ── Bloqueos ─────────────────────────────────────────────────────────

    @Transactional
    public List<AgendaDtos.BlockView> blocks(SaludAccess.Actor actor, Instant from, Instant to) {
        actor.require(AGENDA, VER);
        if (!to.isAfter(from)) throw new IllegalStateException("El rango de fechas está invertido.");
        return blocks.inRange(tenant.getCompanyId(), from, to).stream().map(AppointmentService::toView).toList();
    }

    /**
     * Bloquea a un profesional o un recurso. Si ya hay citas en ese horario
     * no se bloquea: primero hay que moverlas o cancelarlas, para que ningún
     * paciente quede con una cita que nadie va a atender.
     */
    @Transactional
    public AgendaDtos.BlockView createBlock(SaludAccess.Actor actor, AgendaDtos.BlockRequest req) {
        actor.require(AGENDA, EDITAR);
        soloPersonal(actor);
        Long companyId = tenant.getCompanyId();
        if (req.professionalId() == null && req.resourceId() == null) {
            throw new IllegalStateException("Indica a qué profesional o recurso se bloquea.");
        }
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new IllegalStateException("El bloqueo debe terminar después de empezar.");
        }
        if (req.professionalId() != null) {
            professionals.findByUserIdAndCompanyId(req.professionalId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profesional " + req.professionalId() + " no encontrado"));
        }
        if (req.resourceId() != null) findResource(req.resourceId());
        List<Appointment> cruzadas = appointments.activeOverlapping(companyId, req.startsAt(), req.endsAt(),
                        nn(req.professionalId()), nn(req.resourceId()), NINGUNO)
                .stream().filter(x -> !COMPLETADA.equals(x.getStatus())).toList();
        if (!cruzadas.isEmpty()) {
            Appointment x = cruzadas.get(0);
            throw new IllegalStateException("Hay " + cruzadas.size() + " cita(s) en ese horario (la primera, el "
                    + AgendaRules.fecha(x.getStartsAt()) + "). Muévelas o cancélalas antes de bloquear.");
        }
        Block b = new Block();
        b.setCompanyId(companyId);
        b.setProfessionalId(req.professionalId());
        b.setResourceId(req.resourceId());
        b.setStartsAt(req.startsAt());
        b.setEndsAt(req.endsAt());
        b.setReason(blank(req.reason()));
        b.setCreatedBy(actor.userId());
        b.setCreatedAt(Instant.now());
        return toView(blocks.save(b));
    }

    @Transactional
    public void deleteBlock(SaludAccess.Actor actor, Long id) {
        actor.require(AGENDA, EDITAR);
        soloPersonal(actor);
        Block b = blocks.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo " + id + " no encontrado"));
        blocks.delete(b);
    }

    // ── Internos ─────────────────────────────────────────────────────────

    private Appointment find(Long id) {
        return appointments.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita " + id + " no encontrada"));
    }

    private AgendaDtos.AppointmentView view(Long id) {
        AppointmentRow r = rows.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita " + id + " no encontrada"));
        return toView(r);
    }

    private Professional activeProfessional(Long userId) {
        Professional p = professionals.findByUserIdAndCompanyId(userId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Profesional " + userId + " no encontrado"));
        if (!p.isActive()) throw new IllegalStateException("Ese profesional está inactivo en la agenda.");
        return p;
    }

    private AppointmentType activeType(Long typeId) {
        AppointmentType t = types.findByIdAndCompanyId(typeId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de cita " + typeId + " no encontrado"));
        if (!t.isActive()) throw new IllegalStateException("El tipo de cita «" + t.getName() + "» está inactivo.");
        return t;
    }

    private Resource findResource(Long id) {
        return resources.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso " + id + " no encontrado"));
    }

    /** Recurso de la cita: el pedido (activo y de la empresa), obligatorio si el tipo lo exige. */
    private @Nullable Long resourceFor(@Nullable Long resourceId, AppointmentType tipo) {
        if (resourceId == null) {
            if (tipo.isRequiresResource()) {
                throw new IllegalStateException("«" + tipo.getName() + "» necesita un consultorio o recurso.");
            }
            return null;
        }
        Resource r = findResource(resourceId);
        if (!r.isActive()) throw new IllegalStateException("El recurso «" + r.getName() + "» está inactivo.");
        return r.getId();
    }

    /** Para el asistente: la cita tiene que caber en el horario de trabajo del profesional. */
    private void checkSchedule(Long professionalId, Instant start, Instant end) {
        int dia = start.atZone(AgendaRules.GUATEMALA).getDayOfWeek().getValue();
        List<AgendaRules.Window> ventanas = schedules.findByProfessionalIdAndWeekday(professionalId, dia).stream()
                .map(s -> new AgendaRules.Window(s.getStartTime(), s.getEndTime(), s.getResourceId())).toList();
        if (!AgendaRules.withinSchedule(ventanas, start, end)) {
            throw new IllegalStateException("Ese horario está fuera del horario de atención. Consulta los horarios libres.");
        }
    }

    /** Sin bloqueos ni citas activas que se crucen, para el profesional y el recurso. */
    private void checkFree(Long professionalId, @Nullable Long resourceId, Instant start, Instant end, @Nullable Long excluir) {
        Long companyId = tenant.getCompanyId();
        long rec = nn(resourceId);
        for (Block b : blocks.overlapping(companyId, start, end, professionalId, rec)) {
            String quien = professionalId.equals(b.getProfessionalId()) ? "El profesional" : "El recurso";
            throw new IllegalStateException(quien + " está bloqueado de " + AgendaRules.fecha(b.getStartsAt()) + " a "
                    + AgendaRules.fecha(b.getEndsAt()) + (b.getReason() != null ? " (" + b.getReason() + ")" : "") + ".");
        }
        for (Appointment x : appointments.activeOverlapping(companyId, start, end, professionalId, rec, nn(excluir))) {
            String quien = professionalId.equals(x.getProfessionalId()) ? "El profesional" : "El recurso";
            throw new IllegalStateException(quien + " ya tiene una cita de " + AgendaRules.hora(x.getStartsAt()) + " a "
                    + AgendaRules.hora(x.getEndsAt()) + " que se cruza con ese horario.");
        }
    }

    /**
     * Guarda y fuerza el INSERT/UPDATE ya, para que un choque con la
     * restricción de no solape se convierta en un 409 legible en vez de
     * estallar al confirmar la transacción.
     */
    private void guardar(Runnable op) {
        try {
            op.run();
            appointments.flush();
        } catch (PersistenceException e) {
            if (AgendaRules.isOverlapViolation(e)) {
                throw new IllegalStateException("Alguien acaba de ocupar ese horario. Elige otro.");
            }
            throw e;
        }
    }

    /** Un control pendiente de ese paciente, para agendarlo. */
    private FollowUp pendingFollowUp(Long id, Long patientId) {
        FollowUp f = followUps.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Control " + id + " no encontrado"));
        if (!f.getPatientId().equals(patientId)) throw new IllegalStateException("Ese control es de otro paciente.");
        if (!ClinicalRules.FU_PENDIENTE.equals(f.getStatus())) {
            throw new IllegalStateException("Ese control ya está «" + f.getStatus() + "».");
        }
        return f;
    }

    /** La cita de un control se canceló o el paciente no vino: el control vuelve a quedar pendiente. */
    private void liberarControles(Long appointmentId) {
        for (FollowUp f : followUps.findByAppointmentId(appointmentId)) {
            if (ClinicalRules.FU_AGENDADO.equals(f.getStatus())) {
                f.setStatus(ClinicalRules.FU_PENDIENTE);
                f.setAppointmentId(null);
                followUps.update(f);
            }
        }
    }

    private static long nz(@Nullable Long v) {
        return v == null ? 0L : v;
    }

    private static long nn(@Nullable Long v) {
        return v == null ? NINGUNO : v;
    }

    static AgendaDtos.AppointmentView toView(AppointmentRow r) {
        return new AgendaDtos.AppointmentView(r.getId(), r.getPatientId(), r.getPatientName(), r.getPatientPhone(),
                r.getRecordNumber(), r.getProfessionalId(), r.getProfessionalName(), r.getProfessionalColor(),
                r.getTypeId(), r.getTypeName(), r.getTypeColor(), r.getResourceId(), r.getResourceName(),
                r.getStartsAt(), r.getEndsAt(), r.getStatus(), r.getSource(), r.getReason(), r.getNotes(),
                r.getCancelReason(), r.getCreatedAt(), r.getConfirmedAt(), r.getArrivedAt(), r.getStartedAt(),
                r.getCompletedAt(), r.getCancelledAt(), AgendaRules.next(r.getStatus()),
                r.getReminderStatus(), r.getReminderSentAt(), r.getCaseId(), r.getEncounterId(), r.getEncounterStatus(),
                r.getPlanId(), r.getChargeId(), r.getChargeMethod(), r.getChargeTotal(), r.getTypePrice());
    }

    static AgendaDtos.BlockView toView(Block b) {
        return new AgendaDtos.BlockView(b.getId(), b.getProfessionalId(), b.getResourceId(), b.getStartsAt(),
                b.getEndsAt(), b.getReason());
    }
}
