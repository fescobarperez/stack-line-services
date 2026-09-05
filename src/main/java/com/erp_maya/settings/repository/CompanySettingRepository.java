package com.erp_maya.settings.repository;

import com.erp_maya.settings.domain.CompanySetting;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanySettingRepository extends JpaRepository<CompanySetting, Long> {

    List<CompanySetting> findByCompanyId(Long companyId);

    Optional<CompanySetting> findByCompanyIdAndSettingKey(Long companyId, String settingKey);
}
