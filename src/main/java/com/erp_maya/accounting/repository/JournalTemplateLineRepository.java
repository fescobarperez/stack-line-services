package com.erp_maya.accounting.repository;

import com.erp_maya.accounting.domain.JournalTemplateLine;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface JournalTemplateLineRepository extends JpaRepository<JournalTemplateLine, Long> {

    /** Una cuenta que usa alguna plantilla de partida no se borra. */
    boolean existsByAccountId(Long accountId);
}
