package com.erp_maya.authorization.repository;

import com.erp_maya.authorization.domain.*;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Repositorios del motor de autorizaciones, agrupados por ser todos triviales. */
public class AuthorizationRepositories {

    @Repository
    public interface Levels extends JpaRepository<AuthorizationLevel, Long> {
        List<AuthorizationLevel> findByCompanyIdOrderByRankAsc(Long companyId);
        Optional<AuthorizationLevel> findByIdAndCompanyId(Long id, Long companyId);
        Optional<AuthorizationLevel> findByCompanyIdAndCode(Long companyId, String code);
    }

    @Repository
    public interface Types extends JpaRepository<AuthorizationType, Long> {
        List<AuthorizationType> findByCompanyIdOrderByNameAsc(Long companyId);
        Optional<AuthorizationType> findByCompanyIdAndCode(Long companyId, String code);
        Optional<AuthorizationType> findByIdAndCompanyId(Long id, Long companyId);
    }

    @Repository
    public interface Rules extends JpaRepository<AuthorizationRule, Long> {
        List<AuthorizationRule> findByCompanyIdAndTypeIdAndActiveTrue(Long companyId, Long typeId);
        Optional<AuthorizationRule> findByIdAndCompanyId(Long id, Long companyId);
    }

    @Repository
    public interface Requests extends JpaRepository<AuthorizationRequest, Long> {
        Optional<AuthorizationRequest> findByIdAndCompanyId(Long id, Long companyId);
        List<AuthorizationRequest> findByCompanyIdAndStatusOrderByIdDesc(Long companyId, String status);
        List<AuthorizationRequest> findByCompanyIdAndRequestedByOrderByIdDesc(Long companyId, Long requestedBy);
    }

    @Repository
    public interface Steps extends JpaRepository<AuthorizationStep, Long> {
        List<AuthorizationStep> findByRequestIdOrderByIdAsc(Long requestId);
    }

    @Repository
    public interface UserBranches extends JpaRepository<UserBranch, Long> {
        List<UserBranch> findByCompanyIdAndUserId(Long companyId, Long userId);
        boolean existsByUserIdAndBranchId(Long userId, Long branchId);

        /** Aprobadores con alcance sobre la sucursal y rango suficiente. */
        @Query("""
            SELECT ub.userId FROM UserBranch ub
            WHERE ub.companyId = :companyId AND ub.branchId = :branchId
            """)
        List<Long> approverIdsForBranch(Long companyId, Long branchId);
    }
}
