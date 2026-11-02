package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Block;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Bloqueos. Los ids que no aplican llegan como -1 y no como null: Hibernate
 * no siempre sabe el tipo de un parámetro nulo en una consulta nativa.
 */
@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findByIdAndCompanyId(Long id, Long companyId);

    /** Bloqueos que se cruzan con [desde, hasta) para ese profesional o ese recurso. */
    @Query(value = "SELECT * FROM salud_blocks WHERE company_id = :companyId "
            + "AND starts_at < :hasta AND ends_at > :desde "
            + "AND (professional_id = :professionalId OR resource_id = :resourceId) ORDER BY starts_at",
           nativeQuery = true)
    List<Block> overlapping(Long companyId, Instant desde, Instant hasta, long professionalId, long resourceId);

    /** Todos los bloqueos de la empresa que tocan el rango (para pintar la agenda). */
    @Query(value = "SELECT * FROM salud_blocks WHERE company_id = :companyId "
            + "AND starts_at < :hasta AND ends_at > :desde ORDER BY starts_at",
           nativeQuery = true)
    List<Block> inRange(Long companyId, Instant desde, Instant hasta);
}
