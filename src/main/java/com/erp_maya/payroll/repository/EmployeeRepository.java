package com.erp_maya.payroll.repository;

import com.erp_maya.payroll.domain.Employee;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByCompanyId(Long companyId);

    List<Employee> findByCompanyIdAndStatus(Long companyId, String status);

    Optional<Employee> findByIdAndCompanyId(Long id, Long companyId);
}
