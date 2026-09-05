package com.erp_maya.payable.repository;

import com.erp_maya.payable.domain.SupplierPayment;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

@Repository
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    Page<SupplierPayment> findByCompanyIdOrderByPaymentDateDesc(Long companyId, Pageable pageable);
}
