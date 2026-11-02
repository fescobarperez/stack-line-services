package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.SaludCredit;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface SaludCreditRepository extends JpaRepository<SaludCredit, Long> {

    List<SaludCredit> findByPatientIdAndCompanyIdOrderByCreatedAtDesc(Long patientId, Long companyId);

    List<SaludCredit> findByChargeId(Long chargeId);

    /** Movimientos de un tipo en el periodo (reportes: anticipos recibidos). */
    List<SaludCredit> findByCompanyIdAndKindAndCreatedAtGreaterThanEqualsAndCreatedAtLessThan(
            Long companyId, String kind, Instant desde, Instant hasta);

    /** Saldo del anticipo: la suma de los movimientos (nunca negativa, lo cuida SaludBillingService). */
    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM salud_credits WHERE patient_id = :patientId AND company_id = :companyId",
           nativeQuery = true)
    BigDecimal balance(Long patientId, Long companyId);
}

