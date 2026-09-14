package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.Quote;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {

    Page<Quote> findByCompanyIdOrderByQuoteDateDesc(Long companyId, Pageable pageable);

    Page<Quote> findByCompanyIdAndPartyTypeOrderByQuoteDateDesc(Long companyId, String partyType, Pageable pageable);

    List<Quote> findByCompanyIdAndIdIn(Long companyId, List<Long> ids);

    Optional<Quote> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * La cotización con sus líneas y productos ya cargados.
     *
     * Para quien la necesita FUERA de una transacción —el envío de correo, que
     * no puede sostener una conexión mientras habla con el SMTP—: sin el fetch
     * join, recorrer `items` sobre una entidad desprendida lanza
     * LazyInitializationException.
     */
    @Query("SELECT DISTINCT q FROM Quote q "
         + "LEFT JOIN FETCH q.items i LEFT JOIN FETCH i.product "
         + "WHERE q.id = :id AND q.companyId = :companyId")
    Optional<Quote> findWithItems(Long id, Long companyId);
}
