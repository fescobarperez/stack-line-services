package com.erp_maya.asset.repository;

import com.erp_maya.asset.domain.FixedAsset;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FixedAssetRepository extends JpaRepository<FixedAsset, Long> {

    List<FixedAsset> findByCompanyId(Long companyId);

    Optional<FixedAsset> findByIdAndCompanyId(Long id, Long companyId);
}
