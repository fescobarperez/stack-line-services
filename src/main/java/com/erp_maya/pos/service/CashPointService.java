package com.erp_maya.pos.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.pos.domain.CashPoint;
import com.erp_maya.pos.domain.CashRegister;
import com.erp_maya.pos.dto.CashPointDtos;
import com.erp_maya.pos.repository.CashPointRepository;
import com.erp_maya.pos.repository.CashRegisterRepository;
import com.erp_maya.security.domain.User;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class CashPointService {

    private final CashPointRepository points;
    private final CashRegisterRepository sessions;
    private final BranchRepository branches;
    private final TenantContext tenant;

    public CashPointService(CashPointRepository points, CashRegisterRepository sessions,
                            BranchRepository branches, TenantContext tenant) {
        this.points = points;
        this.sessions = sessions;
        this.branches = branches;
        this.tenant = tenant;
    }

    @Transactional
    public List<CashPointDtos.Response> list(Long branchId) {
        Long companyId = tenant.getCompanyId();
        List<CashPoint> rows = branchId != null
                ? points.findByCompanyIdAndBranchIdOrderByCodeAsc(companyId, branchId)
                : points.findByCompanyIdOrderByCodeAsc(companyId);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CashPointDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public CashPointDtos.Response create(CashPointDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        points.findByCompanyIdAndBranchIdAndCode(companyId, req.branchId(), req.code())
                .ifPresent(p -> { throw new IllegalStateException(
                        "Ya existe una caja con código " + req.code() + " en esa sucursal"); });
        CashPoint p = new CashPoint();
        p.setCompanyId(companyId);
        p.setBranch(branch(req.branchId()));
        apply(p, req);
        return toResponse(points.save(p));
    }

    @Transactional
    public CashPointDtos.Response update(Long id, CashPointDtos.Request req) {
        CashPoint p = find(id);
        points.findByCompanyIdAndBranchIdAndCode(tenant.getCompanyId(), req.branchId(), req.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new IllegalStateException(
                        "Ya existe una caja con código " + req.code() + " en esa sucursal"); });
        p.setBranch(branch(req.branchId()));
        apply(p, req);
        return toResponse(points.update(p));
    }

    /** No se borra una caja ocupada: primero hay que cerrar su turno. */
    @Transactional
    public void delete(Long id) {
        CashPoint p = find(id);
        sessions.findByCompanyIdAndCashPointIdAndStatus(tenant.getCompanyId(), id, "open")
                .ifPresent(s -> { throw new IllegalStateException(
                        "La caja " + p.getCode() + " tiene un turno abierto; ciérralo antes de eliminarla"); });
        points.delete(p);
    }

    private void apply(CashPoint p, CashPointDtos.Request req) {
        p.setCode(req.code().trim());
        p.setName(req.name().trim());
        if (req.status() != null && !req.status().isBlank()) p.setStatus(req.status());
    }

    private Branch branch(Long branchId) {
        return branches.findByIdAndCompanyId(branchId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal " + branchId + " no encontrada"));
    }

    private CashPoint find(Long id) {
        return points.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Caja " + id + " no encontrada"));
    }

    private CashPointDtos.Response toResponse(CashPoint p) {
        Branch b = p.getBranch();
        CashRegister open = sessions
                .findByCompanyIdAndCashPointIdAndStatus(p.getCompanyId(), p.getId(), "open")
                .orElse(null);
        User u = open != null ? open.getUser() : null;
        return new CashPointDtos.Response(p.getId(),
                b != null ? b.getId() : null, b != null ? b.getName() : null,
                p.getCode(), p.getName(), p.getStatus(),
                open != null ? open.getId() : null,
                u != null ? u.getId() : null,
                u != null ? u.getName() : null);
    }
}
