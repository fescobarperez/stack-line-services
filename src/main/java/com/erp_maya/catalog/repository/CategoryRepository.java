package com.erp_maya.catalog.repository;

import com.erp_maya.catalog.domain.Category;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByCompanyId(Long companyId);

    Optional<Category> findByIdAndCompanyId(Long id, Long companyId);
}
