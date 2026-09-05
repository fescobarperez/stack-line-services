package com.erp_maya.receivable.repository;

import com.erp_maya.receivable.domain.Payment;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByCompanyIdOrderByPaymentDateDesc(Long companyId, Pageable pageable);

    List<Payment> findByCompanyIdAndClientId(Long companyId, Long clientId);

    Optional<Payment> findByIdAndCompanyId(Long id, Long companyId);

    /** [ saleId, SUM(amount) ] abonos agrupados por venta (para calcular el saldo por documento). */
    @Query("SELECT p.sale.id, SUM(p.amount) FROM Payment p WHERE p.companyId = :companyId AND p.sale.id IS NOT NULL GROUP BY p.sale.id")
    List<Object[]> paidBySale(Long companyId);
}
