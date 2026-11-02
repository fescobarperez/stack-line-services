package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Patient;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Patient> findByCompanyIdAndDocTypeAndDocNumber(Long companyId, String docType, String docNumber);

    /** Cuáles de estos clientes ya son pacientes (búsqueda antes de crear). */
    List<Patient> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);
}
