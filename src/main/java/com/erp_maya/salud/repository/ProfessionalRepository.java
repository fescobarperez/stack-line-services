package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Professional;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfessionalRepository extends JpaRepository<Professional, Long> {

    List<Professional> findByCompanyId(Long companyId);

    Optional<Professional> findByUserIdAndCompanyId(Long userId, Long companyId);
}
