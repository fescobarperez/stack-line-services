package com.erp_maya.uom.repository;

import com.erp_maya.uom.domain.UomConversion;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UomConversionRepository extends JpaRepository<UomConversion, Long> {

    List<UomConversion> findByCompanyId(Long companyId);

    Optional<UomConversion> findByIdAndCompanyId(Long id, Long companyId);
}
