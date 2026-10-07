package com.erp_maya.partner.repository;

import com.erp_maya.partner.domain.Client;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Page<Client> findByCompanyId(Long companyId, Pageable pageable);

    Page<Client> findByCompanyIdAndNameContainsIgnoreCase(Long companyId, String name, Pageable pageable);

    Optional<Client> findByIdAndCompanyId(Long id, Long companyId);

    /** Búsqueda por NIT: identifica al cliente sin depender del nombre escrito. */
    Optional<Client> findByCompanyIdAndNit(Long companyId, String nit);

    /**
     * Búsqueda por teléfono comparando solo dígitos y por el final: el número
     * llega como lo manda WhatsApp (50230063310) y en la ficha puede estar
     * como "3006-3310" o "+502 3006 3310". `ultimos` son los últimos dígitos
     * del número (8 en Guatemala). Devuelve hasta 2 para detectar ambigüedad.
     */
    @Query(value = "SELECT * FROM clients WHERE company_id = :companyId "
            + "AND right(regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g'), :largo) = :ultimos "
            + "ORDER BY id LIMIT 2",
           nativeQuery = true)
    List<Client> findByCompanyIdAndPhoneDigits(Long companyId, String ultimos, int largo);
}
