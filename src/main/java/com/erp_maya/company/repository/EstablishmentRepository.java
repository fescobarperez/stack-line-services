package com.erp_maya.company.repository;

import com.erp_maya.company.domain.Establishment;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstablishmentRepository extends JpaRepository<Establishment, Long> {

    List<Establishment> findByCompanyId(Long companyId);

    Optional<Establishment> findByIdAndCompanyId(Long id, Long companyId);
}
