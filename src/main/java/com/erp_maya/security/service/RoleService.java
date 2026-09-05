package com.erp_maya.security.service;

import com.erp_maya.security.domain.Role;
import com.erp_maya.security.dto.RoleDtos;
import com.erp_maya.security.repository.RoleRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class RoleService {

    private final RoleRepository repository;
    private final TenantContext tenant;

    public RoleService(RoleRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public List<RoleDtos.Response> list() {
        return repository.findByCompanyId(tenant.getCompanyId())
                .stream().map(RoleService::toResponse).toList();
    }

    @Transactional
    public RoleDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public RoleDtos.Response create(RoleDtos.Request req) {
        Role r = new Role();
        r.setCompanyId(tenant.getCompanyId());
        apply(r, req);
        return toResponse(repository.save(r));
    }

    @Transactional
    public RoleDtos.Response update(Long id, RoleDtos.Request req) {
        Role r = find(id);
        apply(r, req);
        return toResponse(repository.update(r));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private void apply(Role r, RoleDtos.Request req) {
        r.setName(req.name());
        r.setDescription(req.description());
        r.setPermissions(req.permissions() != null ? req.permissions() : List.of());
    }

    private Role find(Long id) {
        return repository.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Rol " + id + " no encontrado"));
    }

    private static RoleDtos.Response toResponse(Role r) {
        return new RoleDtos.Response(r.getId(), r.getName(), r.getDescription(), r.getPermissions());
    }
}
