package com.erp_maya.asset.repository;

import com.erp_maya.asset.domain.AssetDepreciation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface AssetDepreciationRepository extends JpaRepository<AssetDepreciation, Long> {

    List<AssetDepreciation> findByCompanyIdAndFixedAssetIdOrderByPeriodDateDesc(Long companyId, Long fixedAssetId);
}
