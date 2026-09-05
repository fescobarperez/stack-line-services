package com.erp_maya.search.repository;

import com.erp_maya.catalog.domain.Product;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Pageable;

import java.util.List;

/** Búsqueda de productos por nombre o SKU (búsqueda global). */
@Repository
public interface SearchProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p.id, p.sku, p.name, p.price FROM Product p WHERE p.companyId = :companyId "
            + "AND (LOWER(p.name) LIKE :term OR LOWER(p.sku) LIKE :term) ORDER BY p.name")
    List<Object[]> search(Long companyId, String term, Pageable pageable);
}
