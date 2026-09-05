package com.erp_maya.payable.repository;

import com.erp_maya.payable.domain.PurchaseInvoice;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {

    Page<PurchaseInvoice> findByCompanyIdOrderByInvoiceDateDesc(Long companyId, Pageable pageable);

    Optional<PurchaseInvoice> findByIdAndCompanyId(Long id, Long companyId);
}
