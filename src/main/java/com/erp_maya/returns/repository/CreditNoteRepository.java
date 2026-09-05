package com.erp_maya.returns.repository;

import com.erp_maya.returns.domain.CreditNote;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    Page<CreditNote> findByCompanyIdOrderByReturnDateDesc(Long companyId, Pageable pageable);

    Optional<CreditNote> findByIdAndCompanyId(Long id, Long companyId);
}
