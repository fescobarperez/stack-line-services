package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.PrescriptionItem;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lista hija de la consulta: se reemplaza completa mientras es borrador. */
@Repository
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

    List<PrescriptionItem> findByEncounterIdOrderByPosition(Long encounterId);

    void deleteByEncounterId(Long encounterId);
}
