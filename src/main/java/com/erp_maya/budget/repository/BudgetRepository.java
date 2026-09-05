package com.erp_maya.budget.repository;

import com.erp_maya.budget.domain.Budget;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByCompanyIdOrderByYearDesc(Long companyId);

    Optional<Budget> findByIdAndCompanyId(Long id, Long companyId);
}
