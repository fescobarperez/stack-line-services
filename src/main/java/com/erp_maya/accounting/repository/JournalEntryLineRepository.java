package com.erp_maya.accounting.repository;

import com.erp_maya.accounting.domain.JournalEntryLine;
import com.erp_maya.accounting.dto.AccountAggregate;
import com.erp_maya.accounting.dto.LedgerLineRow;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, Long> {

    /**
     * Suma débitos y créditos por cuenta para el balance de comprobación / estados
     * financieros. Si periodId es null agrega todos los períodos; si no, filtra por él.
     */
    @Query("""
            SELECT new com.erp_maya.accounting.dto.AccountAggregate(
                       l.account.id, l.account.code, l.account.name, l.account.normalBalance,
                       SUM(l.debit), SUM(l.credit))
            FROM JournalEntryLine l
            WHERE l.companyId = :companyId
              AND (:periodId IS NULL OR l.entry.period.id = :periodId)
            GROUP BY l.account.id, l.account.code, l.account.name, l.account.normalBalance
            ORDER BY l.account.code""")
    List<AccountAggregate> aggregateByAccount(Long companyId, @Nullable Long periodId);

    /** Suma débitos/créditos por cuenta de las pólizas ANTES de una fecha (saldos de apertura). */
    @Query("""
            SELECT new com.erp_maya.accounting.dto.AccountAggregate(
                       l.account.id, l.account.code, l.account.name, l.account.normalBalance,
                       SUM(l.debit), SUM(l.credit))
            FROM JournalEntryLine l
            WHERE l.companyId = :companyId AND l.entry.entryDate < :date
            GROUP BY l.account.id, l.account.code, l.account.name, l.account.normalBalance""")
    List<AccountAggregate> aggregateByAccountBeforeDate(Long companyId, LocalDate date);

    /** Movimientos (líneas de póliza) de una cuenta, opcionalmente de un período, ordenados por fecha. */
    @Query("""
            SELECT new com.erp_maya.accounting.dto.LedgerLineRow(
                       l.entry.entryDate, l.entry.reference, l.entry.description, l.debit, l.credit)
            FROM JournalEntryLine l
            WHERE l.companyId = :companyId AND l.account.id = :accountId
              AND (:periodId IS NULL OR l.entry.period.id = :periodId)
            ORDER BY l.entry.entryDate, l.entry.id""")
    List<LedgerLineRow> movementsForAccount(Long companyId, Long accountId, @Nullable Long periodId);
}
