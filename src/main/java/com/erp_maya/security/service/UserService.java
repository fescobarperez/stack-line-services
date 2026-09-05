package com.erp_maya.security.service;

import com.erp_maya.company.domain.Branch;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.security.domain.Role;
import com.erp_maya.security.domain.User;
import com.erp_maya.authorization.domain.AuthorizationLevel;
import com.erp_maya.authorization.domain.UserBranch;
import com.erp_maya.authorization.repository.AuthorizationRepositories;
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
    private final AuthorizationRepositories.Levels levels;
    private final AuthorizationRepositories.UserBranches userBranches;
    private final TenantContext tenant;

    public UserService(UserRepository users, RoleRepository roles, BranchRepository branches,
                       AuthorizationRepositories.Levels levels,
                       AuthorizationRepositories.UserBranches userBranches,
                       PasswordEncoder passwordEncoder, TenantContext tenant) {
        this.users = users;
        this.roles = roles;
        this.branches = branches;
        this.levels = levels;
        this.userBranches = userBranches;
        this.passwordEncoder = passwordEncoder;
        this.tenant = tenant;
    }

    @Transactional
    public List<UserDtos.Response> list() {
        return users.findByCompanyId(tenant.getCompanyId())
                .stream().map(this::toResponse).toList();
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
        User saved = users.save(u);
        applyBranchScope(saved, req.branchIds());
        return toResponse(saved);
    }

    @Transactional
    public UserDtos.Response update(Long id, UserDtos.Request req) {
        User u = find(id);
        apply(u, req);
        User saved = users.update(u);
        applyBranchScope(saved, req.branchIds());
        return toResponse(saved);
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
        u.setManagerId(req.managerId());
        u.setAuthLevelId(req.authLevelId());
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

    /** Reemplaza el alcance por sucursales del usuario. Vacío = sin alcance. */
    private void applyBranchScope(User u, java.util.List<Long> branchIds) {
        if (branchIds == null) return;
        Long companyId = tenant.getCompanyId();
        userBranches.deleteAll(userBranches.findByCompanyIdAndUserId(companyId, u.getId()));
        for (Long bid : branchIds.stream().distinct().toList()) {
            UserBranch ub = new UserBranch();
            ub.setCompanyId(companyId);
            ub.setUserId(u.getId());
            ub.setBranchId(bid);
            userBranches.save(ub);
        }
    }

    private UserDtos.Response toResponse(User u) {
        Role r = u.getRole();
        Branch b = u.getBranch();
        Long companyId = u.getCompanyId();
        AuthorizationLevel lvl = u.getAuthLevelId() == null ? null
                : levels.findByIdAndCompanyId(u.getAuthLevelId(), companyId).orElse(null);
        String managerName = u.getManagerId() == null ? null
                : users.findByIdAndCompanyId(u.getManagerId(), companyId).map(User::getName).orElse(null);
        java.util.List<Long> scope = userBranches.findByCompanyIdAndUserId(companyId, u.getId())
                .stream().map(UserBranch::getBranchId).toList();
        return new UserDtos.Response(u.getId(), u.getName(), u.getEmail(),
                r != null ? r.getId() : null, r != null ? r.getName() : null,
                b != null ? b.getId() : null, b != null ? b.getName() : null,
                u.getStatus(), u.getLastSeenAt(),
                u.getManagerId(), managerName,
                u.getAuthLevelId(), lvl != null ? lvl.getName() : null, lvl != null ? lvl.getRank() : null,
                scope);
    }
}
