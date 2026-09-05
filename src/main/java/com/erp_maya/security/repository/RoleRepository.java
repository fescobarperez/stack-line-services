package com.erp_maya.security.repository;

import com.erp_maya.security.domain.Role;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByCompanyId(Long companyId);

    Optional<Role> findByIdAndCompanyId(Long id, Long companyId);
}
