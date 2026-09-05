package com.erp_maya.pos.repository;

import com.erp_maya.pos.domain.CashRegister;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    List<CashRegister> findByCompanyId(Long companyId);

    List<CashRegister> findByCompanyIdAndStatus(Long companyId, String status);

    Optional<CashRegister> findByIdAndCompanyId(Long id, Long companyId);

    /** El turno que ocupa una caja ahora mismo. La base garantiza que sea uno solo. */
    Optional<CashRegister> findByCompanyIdAndCashPointIdAndStatus(Long companyId, Long cashPointId, String status);

    /** El turno abierto de un cajero, esté en la caja que esté. */
    Optional<CashRegister> findByCompanyIdAndUserIdAndStatus(Long companyId, Long userId, String status);

    /** Turnos abiertos con fecha operativa anterior: los que hay que cerrar antes de seguir. */
    List<CashRegister> findByCompanyIdAndStatusAndBusinessDateLessThan(Long companyId, String status, LocalDate businessDate);
}
