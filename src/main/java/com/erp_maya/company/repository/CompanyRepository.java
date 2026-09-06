package com.erp_maya.company.repository;

import com.erp_maya.company.domain.Company;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCode(String code);

    /** El NIT es único: el seeder lo consulta antes de crear una empresa. */
    Optional<Company> findByNit(String nit);
}
