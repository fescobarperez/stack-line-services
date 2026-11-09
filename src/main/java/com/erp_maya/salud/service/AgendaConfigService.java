package com.erp_maya.salud.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.salud.domain.AppointmentType;
import com.erp_maya.salud.domain.Professional;
import com.erp_maya.salud.domain.Resource;
import com.erp_maya.salud.domain.Schedule;
import com.erp_maya.salud.dto.AgendaDtos;
import com.erp_maya.salud.repository.AppointmentTypeRepository;
import com.erp_maya.salud.repository.ProfessionalRepository;
import com.erp_maya.salud.repository.ResourceRepository;
import com.erp_maya.salud.repository.ScheduleRepository;
import com.erp_maya.security.dto.UserDtos;
import com.erp_maya.security.service.UserService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.erp_maya.salud.service.PatientRules.blank;
import static com.erp_maya.salud.service.PatientRules.oneOf;
import static com.erp_maya.salud.service.SaludAccess.AGENDA;
import static com.erp_maya.salud.service.SaludAccess.AGENDA_CONFIG;
import static com.erp_maya.salud.service.SaludAccess.EDITAR;
import static com.erp_maya.salud.service.SaludAccess.VER;

/**
 * Configuración de la agenda: profesionales (usuarios con perfil de salud),
 * su horario semanal, recursos y tipos de cita. Leerla basta con ver la
 * agenda (la pantalla la necesita para agendar); cambiarla pide
 * `agenda_config`. No hay borrados: se desactiva, para no romper citas
 * pasadas que los usan.
 */
@Singleton
public class AgendaConfigService {

    private final ProfessionalRepository professionals;
    private final ScheduleRepository schedules;
    private final ResourceRepository resources;
    private final AppointmentTypeRepository types;
    private final UserService users;
    private final TenantContext tenant;

    public AgendaConfigService(ProfessionalRepository professionals, ScheduleRepository schedules,
                               ResourceRepository resources, AppointmentTypeRepository types,
                               UserService users, TenantContext tenant) {
        this.professionals = professionals;
        this.schedules = schedules;
        this.resources = resources;
        this.types = types;
        this.users = users;
        this.tenant = tenant;
    }

    // ── Profesionales ────────────────────────────────────────────────────

    /** Usuarios activos de la empresa con su perfil de salud (si lo tienen) y su horario. */
    @Transactional
    public List<AgendaDtos.ProfessionalView> professionals(SaludAccess.Actor actor) {
        actor.require(AGENDA, VER);
        Long companyId = tenant.getCompanyId();
        Map<Long, Professional> perfiles = new HashMap<>();
        professionals.findByCompanyId(companyId).forEach(p -> perfiles.put(p.getUserId(), p));
        Map<Long, List<AgendaDtos.ScheduleSlot>> horarios = new HashMap<>();
        for (Schedule s : schedules.findByCompanyId(companyId)) {
            horarios.computeIfAbsent(s.getProfessionalId(), k -> new ArrayList<>()).add(toSlot(s));
        }
        List<AgendaDtos.ProfessionalView> out = new ArrayList<>();
        for (UserDtos.Response u : users.list()) {
            Professional p = perfiles.get(u.id());
            // Un usuario inactivo solo aparece si ya era profesional: su historial sigue ahí.
            if (p == null && !"active".equals(u.status())) continue;
            out.add(toView(u, p, horarios.getOrDefault(u.id(), List.of())));
        }
        out.sort(Comparator.comparing((AgendaDtos.ProfessionalView v) -> !v.configured())
                .thenComparing(AgendaDtos.ProfessionalView::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Da de alta o actualiza el perfil de salud de un usuario. */
    @Transactional
    public AgendaDtos.ProfessionalView saveProfessional(SaludAccess.Actor actor, Long userId,
                                                        AgendaDtos.ProfessionalRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        UserDtos.Response u = users.get(userId); // 404 si no es usuario de esta empresa
        Long companyId = tenant.getCompanyId();
        Professional p = professionals.findByUserIdAndCompanyId(userId, companyId).orElse(null);
        boolean nuevo = p == null;
        if (nuevo) {
            p = new Professional();
            p.setUserId(userId);
            p.setCompanyId(companyId);
        }
        p.setSpecialty(blank(req.specialty()));
        p.setLicenseNumber(blank(req.licenseNumber()));
        p.setColor(AgendaRules.color(req.color()));
        p.setActive(req.active() == null || req.active());
        if (nuevo) professionals.save(p); else professionals.update(p);
        List<AgendaDtos.ScheduleSlot> horario = schedules.findByProfessionalIdAndCompanyId(userId, companyId)
                .stream().map(AgendaConfigService::toSlot).toList();
        return toView(u, p, horario);
    }

    /** Reemplaza el horario semanal completo del profesional. */
    @Transactional
    public AgendaDtos.ProfessionalView saveSchedule(SaludAccess.Actor actor, Long userId, AgendaDtos.ScheduleRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        Long companyId = tenant.getCompanyId();
        Professional p = professionals.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new IllegalStateException("Primero da de alta al usuario como profesional."));
        AgendaRules.checkSchedule(req.slots());
        for (AgendaDtos.ScheduleSlot s : req.slots()) {
            if (s.resourceId() != null) findResource(s.resourceId());
        }
        schedules.deleteByProfessionalId(userId);
        for (AgendaDtos.ScheduleSlot s : req.slots()) {
            Schedule e = new Schedule();
            e.setCompanyId(companyId);
            e.setProfessionalId(userId);
            e.setWeekday(s.weekday());
            e.setStartTime(s.start());
            e.setEndTime(s.end());
            e.setResourceId(s.resourceId());
            schedules.save(e);
        }
        List<AgendaDtos.ScheduleSlot> horario = req.slots().stream()
                .sorted(Comparator.comparing(AgendaDtos.ScheduleSlot::weekday).thenComparing(AgendaDtos.ScheduleSlot::start))
                .toList();
        return toView(users.get(userId), p, horario);
    }

    // ── Recursos ─────────────────────────────────────────────────────────

    @Transactional
    public List<AgendaDtos.ResourceView> resources(SaludAccess.Actor actor) {
        actor.require(AGENDA, VER);
        return resources.findByCompanyIdOrderByName(tenant.getCompanyId()).stream().map(AgendaConfigService::toView).toList();
    }

    @Transactional
    public AgendaDtos.ResourceView createResource(SaludAccess.Actor actor, AgendaDtos.ResourceRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        Resource r = new Resource();
        r.setCompanyId(tenant.getCompanyId());
        applyResource(r, req, null);
        return toView(resources.save(r));
    }

    @Transactional
    public AgendaDtos.ResourceView updateResource(SaludAccess.Actor actor, Long id, AgendaDtos.ResourceRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        Resource r = findResource(id);
        applyResource(r, req, id);
        return toView(resources.update(r));
    }

    private void applyResource(Resource r, AgendaDtos.ResourceRequest req, Long id) {
        String nombre = req.name().trim();
        resources.findByCompanyIdAndNameIlike(tenant.getCompanyId(), likeExacto(nombre))
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> { throw new IllegalStateException("Ya hay un recurso llamado «" + otro.getName() + "»."); });
        r.setName(nombre);
        String kind = oneOf(req.kind(), AgendaRules.RESOURCE_KINDS, "Tipo de recurso");
        r.setKind(kind != null ? kind : "consultorio");
        r.setBranchId(req.branchId());
        r.setActive(req.active() == null || req.active());
    }

    Resource findResource(Long id) {
        return resources.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso " + id + " no encontrado"));
    }

    // ── Tipos de cita ────────────────────────────────────────────────────

    @Transactional
    public List<AgendaDtos.TypeView> types(SaludAccess.Actor actor) {
        actor.require(AGENDA, VER);
        return types.findByCompanyIdOrderByName(tenant.getCompanyId()).stream().map(AgendaConfigService::toView).toList();
    }

    @Transactional
    public AgendaDtos.TypeView createType(SaludAccess.Actor actor, AgendaDtos.TypeRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        AppointmentType t = new AppointmentType();
        t.setCompanyId(tenant.getCompanyId());
        applyType(t, req, null);
        return toView(types.save(t));
    }

    @Transactional
    public AgendaDtos.TypeView updateType(SaludAccess.Actor actor, Long id, AgendaDtos.TypeRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        AppointmentType t = types.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de cita " + id + " no encontrado"));
        applyType(t, req, id);
        return toView(types.update(t));
    }

    private void applyType(AppointmentType t, AgendaDtos.TypeRequest req, Long id) {
        String nombre = req.name().trim();
        types.findByCompanyIdAndNameIlike(tenant.getCompanyId(), likeExacto(nombre))
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> { throw new IllegalStateException("Ya hay un tipo de cita llamado «" + otro.getName() + "»."); });
        if (req.price() != null && req.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("El precio no puede ser negativo.");
        }
        t.setName(nombre);
        t.setDurationMinutes(AgendaRules.duration(req.durationMinutes()));
        t.setPrice(req.price());
        t.setColor(AgendaRules.color(req.color()));
        t.setRequiresResource(Boolean.TRUE.equals(req.requiresResource()));
        t.setActive(req.active() == null || req.active());
    }

    // ── Conversión ───────────────────────────────────────────────────────

    /** Para comparar nombres con ILIKE sin que «%» o «_» actúen de comodín. */
    static String likeExacto(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static AgendaDtos.ScheduleSlot toSlot(Schedule s) {
        return new AgendaDtos.ScheduleSlot(s.getWeekday(), s.getStartTime(), s.getEndTime(), s.getResourceId());
    }

    private static AgendaDtos.ProfessionalView toView(UserDtos.Response u, Professional p,
                                                      List<AgendaDtos.ScheduleSlot> horario) {
        return new AgendaDtos.ProfessionalView(u.id(), u.name(), u.email(), p != null,
                p != null ? p.getSpecialty() : null, p != null ? p.getLicenseNumber() : null,
                p != null ? p.getColor() : null, p != null && p.isActive(), horario);
    }

    static AgendaDtos.ResourceView toView(Resource r) {
        return new AgendaDtos.ResourceView(r.getId(), r.getName(), r.getKind(), r.getBranchId(), r.isActive());
    }

    static AgendaDtos.TypeView toView(AppointmentType t) {
        return new AgendaDtos.TypeView(t.getId(), t.getName(), t.getDurationMinutes(), t.getPrice(), t.getColor(),
                t.isRequiresResource(), t.isActive());
    }
}
