package com.erp_maya.quote.repository;

import com.erp_maya.quote.domain.Quote;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.time.Instant;
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

    /** El listado del ERP sin las que el asistente todavía está armando ('abierta'). */
    Page<Quote> findByCompanyIdAndStatusNotEqualOrderByQuoteDateDesc(Long companyId, String status, Pageable pageable);

    Page<Quote> findByCompanyIdAndPartyTypeAndStatusNotEqualOrderByQuoteDateDesc(
            Long companyId, String partyType, String status, Pageable pageable);

    /** Las cotizaciones de un cliente, para que el asistente informe en qué van. */
    @Query("SELECT q FROM Quote q WHERE q.companyId = :companyId AND q.client.id = :clientId "
         + "AND q.partyType = 'client' ORDER BY q.id DESC")
    List<Quote> findByCliente(Long companyId, Long clientId, Pageable pageable);

    /**
     * Cambio de estado condicional: solo si sigue en `desde`. Devuelve cuántas
     * filas cambió (0 = otro llegó primero o ya no estaba en ese estado).
     */
    @Query("UPDATE Quote q SET q.status = :hacia "
         + "WHERE q.id = :id AND q.companyId = :companyId AND q.status = :desde")
    int updateStatusSiEsta(Long id, Long companyId, String desde, String hacia);

    /**
     * prospecto → abierta, solo si ningún vendedor la abrió. Es la carrera que
     * importa: el cliente pide reabrir justo cuando el vendedor la toma; solo
     * uno gana, y lo decide esta sentencia, no un SELECT previo.
     */
    @Query("UPDATE Quote q SET q.status = 'abierta' "
         + "WHERE q.id = :id AND q.companyId = :companyId AND q.status = 'prospecto' AND q.takenAt IS NULL")
    int updateReabrirSiNoTomada(Long id, Long companyId);

    /** prospecto → borrador al abrirla un vendedor, con quién y cuándo. */
    @Query("UPDATE Quote q SET q.status = 'borrador', q.takenBy = :actor, q.takenAt = :ahora "
         + "WHERE q.id = :id AND q.companyId = :companyId AND q.status = 'prospecto' AND q.takenAt IS NULL")
    int updateTomar(Long id, Long companyId, String actor, Instant ahora);

    /** abierta sin actividad desde `limite` → abandonada (todas las empresas). */
    @Query("UPDATE Quote q SET q.status = 'abandonada' WHERE q.status = 'abierta' AND q.updatedAt < :limite")
    int updateAbandonarAntesDe(Instant limite);
}
