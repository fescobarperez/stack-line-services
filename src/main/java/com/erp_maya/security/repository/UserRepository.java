package com.erp_maya.security.repository;

import com.erp_maya.security.domain.User;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByCompanyId(Long companyId);

    Optional<User> findByIdAndCompanyId(Long id, Long companyId);

    Optional<User> findByCompanyIdAndEmail(Long companyId, String email);
}
