package com.erp_maya.fel.repository;

import com.erp_maya.fel.domain.FelDocument;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface FelDocumentRepository extends JpaRepository<FelDocument, Long> {

    Page<FelDocument> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Optional<FelDocument> findByIdAndCompanyId(Long id, Long companyId);

    /** Documentos certificados desde una fecha (límite FEL del plan). */
    long countByCompanyIdAndCertifiedAtGreaterThanEquals(Long companyId, Instant from);

    /** Certificados en {@code [from, to)} (uso y costos por mes). */
    long countByCompanyIdAndCertifiedAtGreaterThanEqualsAndCertifiedAtLessThan(Long companyId, Instant from, Instant to);
}
