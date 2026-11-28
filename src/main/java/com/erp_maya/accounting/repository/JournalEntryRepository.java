package com.erp_maya.accounting.repository;

import com.erp_maya.accounting.domain.JournalEntry;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Page<JournalEntry> findByCompanyIdOrderByEntryDateDesc(Long companyId, Pageable pageable);

    Optional<JournalEntry> findByIdAndCompanyId(Long id, Long companyId);

    /** Las partidas que originó un documento (venta, cobro…). */
    java.util.List<JournalEntry> findByCompanyIdAndSourceTypeAndSourceId(Long companyId, String sourceType, Long sourceId);

    /** Una plantilla que ya generó partidas no se borra: se desactiva. */
    boolean existsByTemplateId(Long templateId);
}
