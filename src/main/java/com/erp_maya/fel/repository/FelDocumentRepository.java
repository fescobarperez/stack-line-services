package com.erp_maya.fel.repository;

import com.erp_maya.fel.domain.FelDocument;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface FelDocumentRepository extends JpaRepository<FelDocument, Long> {

    Page<FelDocument> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Optional<FelDocument> findByIdAndCompanyId(Long id, Long companyId);
}
