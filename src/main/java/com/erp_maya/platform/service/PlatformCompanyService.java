package com.erp_maya.platform.service;

import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.domain.Company;
import com.erp_maya.company.domain.Establishment;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.company.repository.EstablishmentRepository;
import com.erp_maya.platform.domain.CompanyStatusChange;
import com.erp_maya.platform.dto.PlatformCompanyDtos.*;
import com.erp_maya.platform.domain.Plan;
import com.erp_maya.platform.repository.CompanyStatusChangeRepository;
import com.erp_maya.platform.repository.PlanRepository;
import com.erp_maya.platform.security.CompanyStatusCache;
import com.erp_maya.platform.security.PlatformException;
import com.erp_maya.security.domain.Role;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.RoleRepository;
import com.erp_maya.security.repository.UserRepository;
import com.erp_maya.security.service.ServiceTokenService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Empresas vistas desde la consola de plataforma: listado, alta guiada (todo
 * en una transacción), datos, estado e invitaciones del administrador.
 */
@Singleton
public class PlatformCompanyService {

    static final String ADMIN_ROLE = "Administrador";

    private final CompanyRepository companies;
    private final EstablishmentRepository establishments;
    private final BranchRepository branches;
    private final RoleRepository roles;
    private final UserRepository users;
    private final CompanyStatusChangeRepository history;
    private final InvitationService invitations;
    private final CompanyStatusCache statusCache;
    private final PlanRepository plans;
    private final SubscriptionService subscriptions;
    private final PlatformPlanService planService;

    public PlatformCompanyService(CompanyRepository companies, EstablishmentRepository establishments,
                                  BranchRepository branches, RoleRepository roles, UserRepository users,
                                  CompanyStatusChangeRepository history, InvitationService invitations,
                                  CompanyStatusCache statusCache, PlanRepository plans,
                                  SubscriptionService subscriptions, PlatformPlanService planService) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.planService = planService;
        this.companies = companies;
        this.establishments = establishments;
        this.branches = branches;
        this.roles = roles;
        this.users = users;
        this.history = history;
        this.invitations = invitations;
        this.statusCache = statusCache;
    }

    /** Quién actúa, para el historial y las invitaciones. */
    public record Actor(Long operatorId, String name) {}

    // ── Consulta ─────────────────────────────────────────────────────────

    @Transactional
    public ListResponse list(@Nullable String q, @Nullable String status, @Nullable String plan) {
        String texto = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Company> porTexto = companies.findAll().stream()
                .filter(c -> plan == null || plan.isBlank() || plan.equals(planOf(c).code()))
                .filter(c -> texto.isEmpty() || contiene(c.getName(), texto) || contiene(c.getCode(), texto) || contiene(c.getNit(), texto))
                .toList();
        Map<String, Integer> counts = new LinkedHashMap<>();
        CompanyRules.STATUSES.forEach(s -> counts.put(s, 0));
        porTexto.forEach(c -> counts.merge(c.getStatus(), 1, Integer::sum));
        List<Summary> items = porTexto.stream()
                .filter(c -> status == null || status.isBlank() || status.equals(c.getStatus()))
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::summary)
                .toList();
        return new ListResponse(items, items.size(), counts);
    }

    @Transactional
    public CheckCode checkCode(@Nullable String raw) {
        String code = CompanyRules.normalizeCode(raw);
        return new CheckCode(CompanyRules.isValidCode(code) && companies.findByCode(code).isEmpty());
    }

    @Transactional
    public Detail get(Long id) {
        return detail(find(id), null);
    }

    // ── Alta guiada ──────────────────────────────────────────────────────

    @Transactional
    public Detail create(CreateRequest req, Actor actor) {
        CompanyIn c = req.company() == null ? new CompanyIn(null, null, null, null, null, null) : req.company();
        EstablishmentIn e = req.establishment() == null ? new EstablishmentIn(null, null, null, null) : req.establishment();
        BranchIn b = req.branch() == null ? new BranchIn(null, null) : req.branch();
        AdminIn a = req.admin() == null ? new AdminIn(null, null) : req.admin();

        Map<String, String> f = new LinkedHashMap<>();
        String code = CompanyRules.normalizeCode(c.code());
        String nit = CompanyRules.normalizeNit(c.nit());
        String status = c.status() == null ? CompanyRules.TRIAL : c.status();
        String currency = c.baseCurrency() == null ? "GTQ" : c.baseCurrency();
        String email = a.email() == null ? "" : a.email().trim().toLowerCase(Locale.ROOT);
        boolean codeTaken = false;
        boolean nitTaken = false;
        if (!CompanyRules.isValidCode(code)) f.put("company.code", "Código inválido: 3 a 20 caracteres, letras, números o guiones");
        else if (companies.findByCode(code).isPresent()) { f.put("company.code", "Ese código ya está en uso"); codeTaken = true; }
        if (blank(c.name())) f.put("company.name", "El nombre es obligatorio");
        if (!CompanyRules.isValidNit(nit)) f.put("company.nit", "NIT inválido");
        else if (companies.findByNit(nit).isPresent()) { f.put("company.nit", "Ya existe una empresa con ese NIT"); nitTaken = true; }
        if (!CompanyRules.isValidCurrency(currency)) f.put("company.baseCurrency", "Moneda no soportada");
        if (!CompanyRules.TRIAL.equals(status) && !CompanyRules.ACTIVE.equals(status)) f.put("company.status", "Solo prueba o activa");
        Plan plan = c.planId() == null ? null : plans.findById(c.planId()).filter(Plan::isActive).orElse(null);
        if (plan == null) f.put("company.planId", "Elige un plan activo");
        if (e.satCode() == null || e.satCode() < 1) f.put("establishment.satCode", "Código SAT inválido");
        if (blank(e.commercialName())) f.put("establishment.commercialName", "El nombre comercial es obligatorio");
        if (blank(b.name())) f.put("branch.name", "El nombre de la sucursal es obligatorio");
        if (blank(a.name())) f.put("admin.name", "El nombre es obligatorio");
        if (!CompanyRules.isValidEmail(email)) f.put("admin.email", "Correo inválido");
        if (!f.isEmpty()) {
            // Un conflicto de unicidad es 409 aunque venga con otros errores: la UI lo señala en su campo.
            if (codeTaken) throw PlatformException.conflict("CODE_TAKEN", f.get("company.code"), f);
            if (nitTaken) throw PlatformException.conflict("NIT_TAKEN", f.get("company.nit"), f);
            throw PlatformException.validation(f);
        }

        Company company = new Company();
        company.setCode(code);
        company.setName(c.name().trim());
        company.setNit(nit);
        company.setBaseCurrency(currency);
        company.setStatus(status);
        company.setPlan(plan.getCode());
        company = companies.save(company);
        planService.createFor(company.getId(), plan, actor.name());

        Establishment est = new Establishment();
        est.setCompanyId(company.getId());
        est.setSatCode(e.satCode());
        est.setCommercialName(e.commercialName().trim());
        est.setAddress(trimToNull(e.address()));
        est.setPhone(trimToNull(e.phone()));
        est.setStatus("active");
        est = establishments.save(est);

        Branch branch = new Branch();
        branch.setCompanyId(company.getId());
        branch.setEstablishment(est);
        branch.setName(b.name().trim());
        branch.setAddress(trimToNull(b.address()));
        branch.setStatus("active");
        branches.save(branch);

        Role admin = new Role();
        admin.setCompanyId(company.getId());
        admin.setName(ADMIN_ROLE);
        admin.setDescription("Acceso total. Creado al dar de alta la empresa.");
        admin.setPermissions(List.of("*"));
        admin = roles.save(admin);

        User user = new User();
        user.setCompanyId(company.getId());
        user.setRole(admin);
        user.setName(a.name().trim());
        user.setEmail(email);
        user.setStatus("invited");
        user = users.save(user);

        registrar(company.getId(), status, "Alta de la empresa", actor);
        InvitationService.Invitation inv = invitations.issue(user.getId(), company.getId(), email, actor.name());
        return detail(company, inv);
    }

    // ── Cambios ──────────────────────────────────────────────────────────

    @Transactional
    public Detail update(Long id, UpdateRequest req) {
        Company c = find(id);
        Map<String, String> f = new LinkedHashMap<>();
        if (req.name() != null && blank(req.name())) f.put("name", "El nombre es obligatorio");
        String nit = req.nit() == null ? null : CompanyRules.normalizeNit(req.nit());
        if (nit != null) {
            if (!CompanyRules.isValidNit(nit)) f.put("nit", "NIT inválido");
            else if (companies.findByNit(nit).filter(o -> !o.getId().equals(id)).isPresent()) {
                throw PlatformException.conflict("NIT_TAKEN", "Ya existe una empresa con ese NIT", Map.of("nit", "Ya existe una empresa con ese NIT"));
            }
        }
        if (req.baseCurrency() != null && !CompanyRules.isValidCurrency(req.baseCurrency())) f.put("baseCurrency", "Moneda no soportada");
        if (!f.isEmpty()) throw PlatformException.validation(f);
        if (req.name() != null) c.setName(req.name().trim());
        if (nit != null) c.setNit(nit);
        if (req.baseCurrency() != null) c.setBaseCurrency(req.baseCurrency());
        companies.update(c);
        return detail(c, null);
    }

    @Transactional
    public Detail changeStatus(Long id, StatusRequest req, Actor actor) {
        Company c = find(id);
        String to = req.status();
        if (!CompanyRules.canTransition(c.getStatus(), to)) {
            throw PlatformException.conflict("INVALID_TRANSITION", "Ese cambio de estado no está permitido", null);
        }
        String reason = trimToNull(req.reason());
        if (CompanyRules.needsReason(to) && reason == null) {
            throw new PlatformException(HttpStatus.BAD_REQUEST, "VALIDATION", "El motivo es obligatorio", Map.of("reason", "El motivo es obligatorio"));
        }
        c.setStatus(to);
        companies.update(c);
        registrar(c.getId(), to, reason, actor);
        // Las sesiones abiertas de la empresa se cortan en el siguiente request.
        statusCache.invalidate(c.getId());
        return detail(c, null);
    }

    @Transactional
    public InvitationService.Invitation renewInvitation(Long companyId, Long userId, Actor actor) {
        find(companyId);
        User u = users.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> PlatformException.notFound("El administrador no existe"));
        if (!"invited".equals(u.getStatus())) {
            throw PlatformException.conflict("ALREADY_ACTIVE", "Ese administrador ya activó su cuenta", null);
        }
        return invitations.issue(u.getId(), companyId, u.getEmail(), actor.name());
    }

    // ── Vistas ───────────────────────────────────────────────────────────

    private Company find(Long id) {
        return companies.findById(id).orElseThrow(() -> PlatformException.notFound("La empresa no existe"));
    }

    private void registrar(Long companyId, String status, @Nullable String reason, Actor actor) {
        CompanyStatusChange h = new CompanyStatusChange();
        h.setCompanyId(companyId);
        h.setStatus(status);
        h.setReason(reason);
        h.setOperatorId(actor.operatorId());
        h.setOperatorName(actor.name());
        history.save(h);
    }

    /** Usuarios de la empresa sin el de sistema del asistente. */
    private List<User> personas(Long companyId) {
        return users.findByCompanyId(companyId).stream()
                .filter(u -> !ServiceTokenService.STATUS_SYSTEM.equals(u.getStatus()))
                .toList();
    }

    private PlanRef planOf(Company c) {
        return PlatformPlanService.ref(subscriptions.effective(c.getId()).plan());
    }

    private Summary summary(Company c) {
        List<User> gente = personas(c.getId());
        int sucursales = (int) branches.findByCompanyId(c.getId()).stream().filter(b -> "active".equals(b.getStatus())).count();
        return new Summary(c.getId(), c.getCode(), c.getName(), c.getNit(), c.getStatus(), planOf(c),
                c.getBaseCurrency(), activos(gente), sucursales, c.getCreatedAt(), ultimaActividad(gente));
    }

    private Detail detail(Company c, @Nullable InvitationService.Invitation inv) {
        List<User> gente = personas(c.getId());
        List<EstablishmentView> ests = establishments.findByCompanyId(c.getId()).stream()
                .sorted(Comparator.comparing(Establishment::getSatCode))
                .map(e -> new EstablishmentView(e.getId(), e.getSatCode(), e.getCommercialName(), e.getAddress(), e.getPhone()))
                .toList();
        List<BranchView> sucs = branches.findByCompanyId(c.getId()).stream()
                .sorted(Comparator.comparing(Branch::getId))
                .map(b -> new BranchView(b.getId(), b.getName(), b.getAddress(),
                        b.getEstablishment() == null ? null : b.getEstablishment().getId(), b.getStatus()))
                .toList();
        List<AdminView> admins = gente.stream()
                .filter(PlatformCompanyService::esAdmin)
                .sorted(Comparator.comparing(User::getId))
                .map(u -> new AdminView(u.getId(), u.getName(), u.getEmail(), u.getStatus(), u.getLastSeenAt()))
                .toList();
        List<StatusEntry> hist = new ArrayList<>();
        history.findByCompanyIdOrderByCreatedAtAsc(c.getId())
                .forEach(h -> hist.add(new StatusEntry(h.getStatus(), h.getReason(), h.getOperatorName(), h.getCreatedAt())));
        return new Detail(c.getId(), c.getCode(), c.getName(), c.getNit(), c.getStatus(), planOf(c),
                c.getBaseCurrency(), activos(gente), c.getCreatedAt(), ultimaActividad(gente),
                ests, sucs, admins, hist, inv);
    }

    /** Administrador = rol con permiso total («*»). */
    static boolean esAdmin(User u) {
        Role r = u.getRole();
        return r != null && r.getPermissions() != null && r.getPermissions().contains("*");
    }

    private static int activos(List<User> gente) {
        return (int) gente.stream().filter(u -> "active".equals(u.getStatus())).count();
    }

    private static @Nullable Instant ultimaActividad(List<User> gente) {
        return gente.stream().map(User::getLastSeenAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    private static boolean contiene(@Nullable String v, String texto) {
        return v != null && v.toLowerCase(Locale.ROOT).contains(texto);
    }

    private static boolean blank(@Nullable String s) {
        return s == null || s.isBlank();
    }

    private static @Nullable String trimToNull(@Nullable String s) {
        return blank(s) ? null : s.trim();
    }
}
