package com.erp_maya.partner.repository;

import com.erp_maya.partner.domain.Client;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Page<Client> findByCompanyId(Long companyId, Pageable pageable);

    Page<Client> findByCompanyIdAndNameContainsIgnoreCase(Long companyId, String name, Pageable pageable);

    Optional<Client> findByIdAndCompanyId(Long id, Long companyId);

    /** Búsqueda por NIT: identifica al cliente sin depender del nombre escrito. */
    Optional<Client> findByCompanyIdAndNit(Long companyId, String nit);
}
