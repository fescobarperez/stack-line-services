package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.QuoteCondition;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteConditionRepository extends JpaRepository<QuoteCondition, Long> {

    // Micronaut Data no admite dos criterios de orden en el nombre del método
    // (OrderBy...AscIdAsc): se escribe la consulta. El id desempata el orden.
    @Query("SELECT c FROM QuoteCondition c WHERE c.companyId = :companyId ORDER BY c.sortOrder ASC, c.id ASC")
    List<QuoteCondition> listarPorEmpresa(Long companyId);

    @Query("SELECT c FROM QuoteCondition c WHERE c.companyId = :companyId AND c.active = true ORDER BY c.sortOrder ASC, c.id ASC")
    List<QuoteCondition> listarActivas(Long companyId);

    Optional<QuoteCondition> findByIdAndCompanyId(Long id, Long companyId);
}
