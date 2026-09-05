package com.erp_maya.authorization.service;

import com.erp_maya.authorization.domain.*;
import com.erp_maya.authorization.dto.AuthorizationDtos;
import com.erp_maya.authorization.repository.AuthorizationRepositories.*;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.UserRepository;
import com.erp_maya.security.service.PasswordEncoder;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Motor de autorizaciones. Genérico: no sabe qué es un descuento.
 *
 * Un módulo consumidor solo hace tres cosas:
 *   1. evaluate(...)  ¿hace falta autorización y de qué nivel?
 *   2. create(...)    la pide (aprobada al vuelo con PIN, o pendiente en bandeja)
 *   3. resolve(...)   alguien decide
 *
 * Quién puede aprobar: cualquier usuario con rango >= el que exige la regla y
 * con alcance sobre la sucursal de la solicitud. No hace falta que sea el jefe
 * directo —"basta con que sea un superior"—, y nunca puede ser el solicitante.
 */
@Singleton
public class AuthorizationService {

    private static final String PENDING = "pending";

    private final Types types;
    private final Rules rules;
    private final Levels levels;
    private final Requests requests;
    private final Steps steps;
    private final UserBranches userBranches;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TenantContext tenant;

    public AuthorizationService(Types types, Rules rules, Levels levels, Requests requests,
                                Steps steps, UserBranches userBranches, UserRepository users,
                                PasswordEncoder passwords, TenantContext tenant) {
        this.types = types; this.rules = rules; this.levels = levels;
        this.requests = requests; this.steps = steps; this.userBranches = userBranches;
        this.users = users; this.passwords = passwords; this.tenant = tenant;
    }

    // ── 1. Evaluación ────────────────────────────────────────────────────
    @Transactional
    public AuthorizationDtos.EvaluateResponse evaluate(AuthorizationDtos.EvaluateRequest req) {
        Long companyId = tenant.getCompanyId();
        AuthorizationType type = type(companyId, req.type());
        if (!Boolean.TRUE.equals(type.getActive())) {
            return new AuthorizationDtos.EvaluateResponse(false, null, null, null, null,
                    type.getResolutionMode(), "El tipo está inactivo");
        }
        AuthorizationRule rule = matchRule(companyId, type.getId(), req.amount(), req.currency(), req.percent());
        if (rule == null) {
            return new AuthorizationDtos.EvaluateResponse(false, null, null, null, null,
                    type.getResolutionMode(), "Ninguna regla aplica: no requiere autorización");
        }
        AuthorizationLevel level = levels.findByIdAndCompanyId(rule.getLevelId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Nivel " + rule.getLevelId() + " no encontrado"));
        return new AuthorizationDtos.EvaluateResponse(true, rule.getId(),
                level.getId(), level.getName(), level.getRank(),
                type.getResolutionMode(), "Requiere " + level.getName());
    }

    /**
     * La banda que aplica. Se compara contra el valor de la unidad de la regla:
     * las de 'percent' miran el porcentaje, las de 'amount' el monto. Si varias
     * encajan gana la de mayor rango exigido: el umbral más estricto manda.
     */
    private AuthorizationRule matchRule(Long companyId, Long typeId,
                                        BigDecimal amount, String currency, BigDecimal percent) {
        AuthorizationRule best = null; short bestRank = -1;
        for (AuthorizationRule r : rules.findByCompanyIdAndTypeIdAndActiveTrue(companyId, typeId)) {
            BigDecimal value = "percent".equals(r.getUnit()) ? percent : amount;
            if (value == null) continue;
            // Un umbral en dinero solo compara contra la misma moneda. Sin
            // conversión todavía: cuando existan tipos de cambio, aquí entra.
            if ("amount".equals(r.getUnit()) && currency != null
                    && r.getCurrency() != null && !r.getCurrency().equals(currency)) continue;
            if (value.compareTo(r.getMinValue()) <= 0) continue;
            if (r.getMaxValue() != null && value.compareTo(r.getMaxValue()) > 0) continue;
            Short rank = levels.findByIdAndCompanyId(r.getLevelId(), companyId)
                    .map(AuthorizationLevel::getRank).orElse((short) 0);
            if (rank > bestRank) { best = r; bestRank = rank; }
        }
        return best;
    }

    // ── 2. Alta ──────────────────────────────────────────────────────────
    @Transactional
    public AuthorizationDtos.Response create(AuthorizationDtos.CreateRequest req, Long requesterId) {
        Long companyId = tenant.getCompanyId();
        AuthorizationType type = type(companyId, req.type());
        AuthorizationRule rule = matchRule(companyId, type.getId(), req.amount(), req.currency(), req.percent());
        if (rule == null) {
            throw new IllegalStateException("Esta operación no requiere autorización");
        }

        AuthorizationRequest r = new AuthorizationRequest();
        r.setCompanyId(companyId);
        r.setTypeId(type.getId());
        r.setRuleId(rule.getId());
        r.setRequestedBy(requesterId);
        r.setBranchId(req.branchId());
        r.setAmount(req.amount());
        r.setCurrency(req.currency());
        r.setPercent(req.percent());
        r.setPayload(req.payload() != null ? req.payload() : "{}");
        r.setReference(req.reference());
        r.setStatus(PENDING);

        boolean inPlace = req.approverEmail() != null && !req.approverEmail().isBlank();
        if (inPlace && "tray".equals(type.getResolutionMode())) {
            throw new IllegalStateException("El tipo " + type.getCode() + " solo se resuelve por bandeja");
        }
        if (!inPlace && "pin".equals(type.getResolutionMode())) {
            throw new IllegalStateException("El tipo " + type.getCode() + " requiere autorización en sitio");
        }
        r.setResolutionMode(inPlace ? "pin" : "tray");
        AuthorizationRequest saved = requests.save(r);

        if (inPlace) {
            // Resolución en el mismo acto: el aprobador teclea su credencial.
            decide(saved, rule, req.approverEmail(), req.approverPassword(), "approved", null, requesterId);
        }
        return toResponse(saved);
    }

    // ── 3. Resolución ────────────────────────────────────────────────────
    @Transactional
    public AuthorizationDtos.Response resolve(Long id, AuthorizationDtos.ResolveRequest req, Long actingUserId) {
        Long companyId = tenant.getCompanyId();
        AuthorizationRequest r = requests.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud " + id + " no encontrada"));
        if (!PENDING.equals(r.getStatus())) {
            throw new IllegalStateException("La solicitud ya está " + r.getStatus());
        }
        AuthorizationRule rule = r.getRuleId() == null ? null
                : rules.findByIdAndCompanyId(r.getRuleId(), companyId).orElse(null);
        decide(r, rule, req.approverEmail(), req.approverPassword(), req.decision(), req.comment(), actingUserId);
        return toResponse(r);
    }

    /**
     * Valida al aprobador y sella la decisión. Con credencial (PIN) valida
     * contraseña; sin ella, el aprobador es el usuario autenticado (bandeja).
     */
    private void decide(AuthorizationRequest r, AuthorizationRule rule,
                        String approverEmail, String approverPassword,
                        String decision, String comment, Long actingUserId) {
        Long companyId = r.getCompanyId();
        User approver;
        if (approverEmail != null && !approverEmail.isBlank()) {
            approver = users.findByCompanyIdAndEmail(companyId, approverEmail)
                    .orElseThrow(() -> new IllegalStateException("Credenciales de autorización inválidas"));
            if (approverPassword == null
                    || !passwords.matches(approverPassword, approver.getPasswordHash())) {
                throw new IllegalStateException("Credenciales de autorización inválidas");
            }
        } else {
            approver = users.findByIdAndCompanyId(actingUserId, companyId)
                    .orElseThrow(() -> new IllegalStateException("Aprobador no identificado"));
        }
        if (!"active".equals(approver.getStatus())) {
            throw new IllegalStateException("El usuario autorizador está inactivo");
        }
        if (approver.getId().equals(r.getRequestedBy())) {
            throw new IllegalStateException("No puedes autorizar tu propia solicitud");
        }
        requireAuthority(approver, rule, r.getBranchId(), companyId);

        AuthorizationStep step = new AuthorizationStep();
        step.setCompanyId(companyId);
        step.setRequestId(r.getId());
        step.setApproverId(approver.getId());
        step.setDecision("rejected".equals(decision) ? "rejected" : "approved");
        step.setComment(comment);
        steps.save(step);

        r.setStatus(step.getDecision());
        r.setResolvedAt(Instant.now());
        requests.update(r);
    }

    /** Rango suficiente y alcance sobre la sucursal. */
    private void requireAuthority(User approver, AuthorizationRule rule, Long branchId, Long companyId) {
        if (rule != null) {
            short needed = levels.findByIdAndCompanyId(rule.getLevelId(), companyId)
                    .map(AuthorizationLevel::getRank).orElse((short) 0);
            short has = approver.getAuthLevelId() == null ? -1
                    : levels.findByIdAndCompanyId(approver.getAuthLevelId(), companyId)
                        .map(AuthorizationLevel::getRank).orElse((short) -1);
            if (has < needed) {
                throw new IllegalStateException("El usuario no tiene nivel suficiente para autorizar esto");
            }
        }
        if (branchId != null && !userBranches.existsByUserIdAndBranchId(approver.getId(), branchId)) {
            throw new IllegalStateException("El usuario no tiene alcance sobre esta sucursal");
        }
    }

    // ── Consultas ────────────────────────────────────────────────────────
    @Transactional
    public List<AuthorizationDtos.Response> pending() {
        return requests.findByCompanyIdAndStatusOrderByIdDesc(tenant.getCompanyId(), PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AuthorizationDtos.Response get(Long id) {
        return toResponse(requests.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud " + id + " no encontrada")));
    }

    private AuthorizationType type(Long companyId, String code) {
        return types.findByCompanyIdAndCode(companyId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de autorización '" + code + "' no configurado"));
    }

    private AuthorizationDtos.Response toResponse(AuthorizationRequest r) {
        Long companyId = r.getCompanyId();
        AuthorizationType t = types.findByIdAndCompanyId(r.getTypeId(), companyId).orElse(null);
        Optional<AuthorizationRule> rule = r.getRuleId() == null ? Optional.empty()
                : rules.findByIdAndCompanyId(r.getRuleId(), companyId);
        AuthorizationLevel level = rule.flatMap(x -> levels.findByIdAndCompanyId(x.getLevelId(), companyId)).orElse(null);
        String requesterName = users.findByIdAndCompanyId(r.getRequestedBy(), companyId)
                .map(User::getName).orElse(null);
        List<AuthorizationDtos.StepResponse> stepList = steps.findByRequestIdOrderByIdAsc(r.getId())
                .stream().map(s -> new AuthorizationDtos.StepResponse(s.getId(), s.getApproverId(),
                        users.findByIdAndCompanyId(s.getApproverId(), companyId).map(User::getName).orElse(null),
                        s.getDecision(), s.getComment(), s.getCreatedAt())).toList();
        return new AuthorizationDtos.Response(r.getId(),
                t != null ? t.getCode() : null, t != null ? t.getName() : null, r.getRuleId(),
                r.getRequestedBy(), requesterName, r.getBranchId(),
                r.getAmount(), r.getCurrency(), r.getPercent(), r.getPayload(),
                r.getStatus(), r.getResolutionMode(), r.getReference(),
                level != null ? level.getId() : null, level != null ? level.getName() : null,
                r.getCreatedAt(), r.getResolvedAt(), stepList);
    }
}
