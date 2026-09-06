package com.erp_maya.partner.repository;

import com.erp_maya.partner.domain.ClientBalance;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lectura de v_client_balance: la definición única de cuentas por cobrar. */
@Repository
public interface ClientBalanceRepository extends JpaRepository<ClientBalance, Long> {

    /** Todos los saldos de la empresa en una consulta: evita el N+1 de la lista. */
    List<ClientBalance> findByCompanyId(Long companyId);
}
