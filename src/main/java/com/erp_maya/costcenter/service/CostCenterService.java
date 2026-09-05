package com.erp_maya.costcenter.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.costcenter.domain.CostCenter;
import com.erp_maya.costcenter.dto.CostCenterDtos;
import com.erp_maya.costcenter.repository.CostCenterRepository;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.repository.UserRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Singleton
public class CostCenterService {

    private final CostCenterRepository centers;
    private final UserRepository users;
    private final TenantContext tenant;

    public CostCenterService(CostCenterRepository centers, UserRepository users, TenantContext tenant) {
        this.centers = centers;
        this.users = users;
        this.tenant = tenant;
    }

    @Transactional
    public List<CostCenterDtos.Response> list() {
        return centers.findByCompanyIdOrderByCode(tenant.getCompanyId())
                .stream().map(CostCenterService::toResponse).toList();
    }

    @Transactional
    public CostCenterDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public CostCenterDtos.Response create(CostCenterDtos.Request req) {
        CostCenter c = new CostCenter();
        c.setCompanyId(tenant.getCompanyId());
        apply(c, req);
        return toResponse(centers.save(c));
    }

    @Transactional
    public CostCenterDtos.Response update(Long id, CostCenterDtos.Request req) {
        CostCenter c = find(id);
        apply(c, req);
        return toResponse(centers.update(c));
    }

    @Transactional
    public void delete(Long id) {
        centers.delete(find(id));
    }

    private void apply(CostCenter c, CostCenterDtos.Request req) {
        c.setCode(req.code());
        c.setName(req.name());
        c.setCostGroup(req.costGroup());
        c.setCenterType(req.centerType());
        c.setBudget(req.budget() != null ? req.budget() : BigDecimal.ZERO);
        c.setActive(req.active() != null ? req.active() : Boolean.TRUE);
        if (req.responsibleUserId() != null) {
            User u = users.findByIdAndCompanyId(req.responsibleUserId(), tenant.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario " + req.responsibleUserId() + " no encontrado"));
            c.setResponsible(u);
        } else {
            c.setResponsible(null);
        }
    }

    private CostCenter find(Long id) {
        return centers.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Centro de costo " + id + " no encontrado"));
    }

    private static CostCenterDtos.Response toResponse(CostCenter c) {
        User u = c.getResponsible();
        return new CostCenterDtos.Response(c.getId(), c.getCode(), c.getName(), c.getCostGroup(),
                c.getCenterType(), u != null ? u.getId() : null, u != null ? u.getName() : null,
                c.getBudget(), c.getActive());
    }
}
