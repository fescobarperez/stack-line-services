package com.erp_maya.uom.repository;

import com.erp_maya.uom.domain.UnitOfMeasure;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {

    List<UnitOfMeasure> findByCompanyId(Long companyId);

    Optional<UnitOfMeasure> findByIdAndCompanyId(Long id, Long companyId);
}
