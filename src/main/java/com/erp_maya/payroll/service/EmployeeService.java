package com.erp_maya.payroll.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.company.repository.BranchRepository;
import com.erp_maya.payroll.domain.Employee;
import com.erp_maya.payroll.dto.EmployeeDtos;
import com.erp_maya.payroll.repository.EmployeeRepository;
import com.erp_maya.security.repository.UserRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Singleton
public class EmployeeService {

    private final EmployeeRepository employees;
    private final BranchRepository branches;
    private final UserRepository users;
    private final TenantContext tenant;

    public EmployeeService(EmployeeRepository employees, BranchRepository branches,
                           UserRepository users, TenantContext tenant) {
        this.employees = employees;
        this.branches = branches;
        this.users = users;
        this.tenant = tenant;
    }

    @Transactional
    public List<EmployeeDtos.Response> list() {
        return employees.findByCompanyId(tenant.getCompanyId())
                .stream().map(EmployeeService::toResponse).toList();
    }

    @Transactional
    public EmployeeDtos.Response get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public EmployeeDtos.Response create(EmployeeDtos.Request req) {
        Employee e = new Employee();
        e.setCompanyId(tenant.getCompanyId());
        apply(e, req);
        return toResponse(employees.save(e));
    }

    @Transactional
    public EmployeeDtos.Response update(Long id, EmployeeDtos.Request req) {
        Employee e = find(id);
        apply(e, req);
        return toResponse(employees.update(e));
    }

    @Transactional
    public void delete(Long id) {
        employees.delete(find(id));
    }

    private void apply(Employee e, EmployeeDtos.Request req) {
        e.setEmployeeCode(req.employeeCode());
        e.setName(req.name());
        e.setDepartment(req.department());
        e.setPosition(req.position());
        e.setSalary(req.salary() != null ? req.salary() : BigDecimal.ZERO);
        e.setStatus(req.status() != null ? req.status() : "active");
        e.setHiredDate(req.hiredDate());
        e.setDpi(req.dpi());
        e.setNit(req.nit());
        e.setBankName(req.bankName());
        e.setBankAccount(req.bankAccount());
        e.setBranch(req.branchId() == null ? null
                : branches.findByIdAndCompanyId(req.branchId(), tenant.getCompanyId()).orElse(null));
        e.setUser(req.userId() == null ? null
                : users.findByIdAndCompanyId(req.userId(), tenant.getCompanyId()).orElse(null));
    }

    private Employee find(Long id) {
        return employees.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado " + id + " no encontrado"));
    }

    private static EmployeeDtos.Response toResponse(Employee e) {
        return new EmployeeDtos.Response(e.getId(), e.getEmployeeCode(), e.getName(), e.getDepartment(),
                e.getPosition(), e.getSalary(), e.getStatus(), e.getHiredDate(), e.getDpi(), e.getNit(),
                e.getBankName(), e.getBankAccount(),
                e.getBranch() != null ? e.getBranch().getId() : null,
                e.getUser() != null ? e.getUser().getId() : null);
    }
}
