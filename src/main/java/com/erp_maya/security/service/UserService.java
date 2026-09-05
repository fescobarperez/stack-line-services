package com.erp_maya.security.service;

import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.security.domain.Role;
import com.erp_maya.security.domain.User;
import com.erp_maya.security.dto.UserDtos;
import com.erp_maya.security.repository.RoleRepository;
import com.erp_maya.security.repository.UserRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

@Singleton
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final BranchRepository branches;
    private final PasswordEncoder passwordEncoder;
    private final TenantContext tenant;

    public UserService(UserRepository users, RoleRepository roles, BranchRepository branches,
                       PasswordEncoder passwordEncoder, TenantContext tenant) {
        this.users = users;
        this.roles = roles;
        this.branches = branches;
        this.passwordEncoder = passwordEncoder;
        this.tenant = tenant;
    }

    @Transactional
    public List<UserDtos.Response> list() {
        return users.findByCompanyId(tenant.getCompanyId())
                .stream().map(UserService::toResponse).toList();
    }

    @Transactional
    public UserDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public UserDtos.Response create(UserDtos.Request req) {
        User u = new User();
        u.setCompanyId(tenant.getCompanyId());
        apply(u, req);
        return toResponse(users.save(u));
    }

    @Transactional
    public UserDtos.Response update(Long id, UserDtos.Request req) {
        User u = find(id);
        apply(u, req);
        return toResponse(users.update(u));
    }

    @Transactional
    public void delete(Long id) {
        users.delete(find(id));
    }

    private void apply(User u, UserDtos.Request req) {
        u.setName(req.name());
        u.setEmail(req.email());
        u.setStatus(req.status() != null ? req.status() : "active");
        u.setRole(resolveRole(req.roleId()));
        u.setBranch(resolveBranch(req.branchId()));
        // Solo re-hashea si viene password (en update se omite para conservar el actual).
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(req.password()));
        }
    }

    private Role resolveRole(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roles.findByIdAndCompanyId(roleId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Rol " + roleId + " no encontrado"));
    }

    private Branch resolveBranch(Long branchId) {
        if (branchId == null) {
            return null;
        }
        return branches.findByIdAndCompanyId(branchId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal " + branchId + " no encontrada"));
    }

    private User find(Long id) {
        return users.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario " + id + " no encontrado"));
    }

    private static UserDtos.Response toResponse(User u) {
        Role r = u.getRole();
        Branch b = u.getBranch();
        return new UserDtos.Response(u.getId(), u.getName(), u.getEmail(),
                r != null ? r.getId() : null, r != null ? r.getName() : null,
                b != null ? b.getId() : null, b != null ? b.getName() : null,
                u.getStatus(), u.getLastSeenAt());
    }
}
