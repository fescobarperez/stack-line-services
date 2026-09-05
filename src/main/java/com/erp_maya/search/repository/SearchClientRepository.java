package com.erp_maya.search.repository;

import com.erp_maya.partner.domain.Client;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Pageable;

import java.util.List;

/** Búsqueda de clientes por nombre o NIT (búsqueda global). */
@Repository
public interface SearchClientRepository extends JpaRepository<Client, Long> {

    @Query("SELECT c.id, c.name, c.nit FROM Client c WHERE c.companyId = :companyId "
            + "AND (LOWER(c.name) LIKE :term OR LOWER(c.nit) LIKE :term) ORDER BY c.name")
    List<Object[]> search(Long companyId, String term, Pageable pageable);
}
