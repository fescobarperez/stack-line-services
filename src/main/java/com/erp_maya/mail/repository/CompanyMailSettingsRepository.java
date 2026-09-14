package com.erp_maya.mail.repository;

import com.erp_maya.mail.domain.CompanyMailSettings;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface CompanyMailSettingsRepository extends JpaRepository<CompanyMailSettings, Long> {
}
