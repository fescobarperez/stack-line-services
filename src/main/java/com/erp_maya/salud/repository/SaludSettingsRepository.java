package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.SaludSettings;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface SaludSettingsRepository extends JpaRepository<SaludSettings, Long> {
}
