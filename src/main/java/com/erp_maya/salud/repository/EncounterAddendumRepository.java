package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.EncounterAddendum;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface EncounterAddendumRepository extends JpaRepository<EncounterAddendum, Long> {

    List<EncounterAddendum> findByEncounterIdOrderByCreatedAt(Long encounterId);
}
