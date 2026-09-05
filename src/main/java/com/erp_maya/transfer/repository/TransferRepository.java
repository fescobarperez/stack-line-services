package com.erp_maya.transfer.repository;

import com.erp_maya.transfer.domain.Transfer;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Page<Transfer> findByCompanyIdOrderByTransferDateDesc(Long companyId, Pageable pageable);

    Optional<Transfer> findByIdAndCompanyId(Long id, Long companyId);
}
