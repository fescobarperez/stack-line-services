package com.erp_maya.platform.repository;

import com.erp_maya.platform.domain.PlatformOperator;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Repository
public interface PlatformOperatorRepository extends JpaRepository<PlatformOperator, Long> {

    Optional<PlatformOperator> findByEmail(String email);
}
