package com.erp_maya.platform.repository;

import com.erp_maya.platform.domain.UserInvitation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {

    Optional<UserInvitation> findByTokenHash(String tokenHash);

    List<UserInvitation> findByUserId(Long userId);
}
