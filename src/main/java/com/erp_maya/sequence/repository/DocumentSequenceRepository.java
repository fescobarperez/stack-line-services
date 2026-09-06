package com.erp_maya.sequence.repository;

import com.erp_maya.sequence.domain.DocumentSequence;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Repository
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    /**
     * Reserva la fila con FOR UPDATE. Dos cajeros cobrando en el mismo instante
     * se serializan aquí: el segundo espera a que el primero confirme su
     * incremento, así que no pueden sacar el mismo correlativo.
     */
    @Query(value = "SELECT * FROM document_sequences WHERE company_id = :companyId "
            + "AND doc_type = :docType AND series = :series FOR UPDATE",
           nativeQuery = true)
    Optional<DocumentSequence> lockFor(Long companyId, String docType, String series);

    Optional<DocumentSequence> findByCompanyIdAndDocTypeAndSeries(
            Long companyId, String docType, String series);
}
