package com.erp_maya.catalog.repository;

import com.erp_maya.catalog.domain.Product;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByCompanyId(Long companyId, Pageable pageable);

    Page<Product> findByCompanyIdAndNameContainsIgnoreCase(Long companyId, String name, Pageable pageable);

    Page<Product> findByCompanyIdAndItemType(Long companyId, String itemType, Pageable pageable);

    Page<Product> findByCompanyIdAndItemTypeAndNameContainsIgnoreCase(
            Long companyId, String itemType, String name, Pageable pageable);

    /**
     * Búsqueda por nombre o SKU sin distinguir mayúsculas NI tildes: el
     * cliente escribe «pergola» y el producto se llama «Pérgola». `texto` ya
     * llega en minúsculas y sin tildes (ProductService.normalizar); aquí se
     * quitan las del nombre con translate(), sin depender de la extensión
     * unaccent de PostgreSQL. El cast es obligatorio: Hibernate no conoce el
     * tipo que devuelve function('translate', …) y rechaza el LIKE sobre Object.
     */
    @Query(value = "SELECT p FROM Product p WHERE p.companyId = :companyId AND ("
            + "cast(function('translate', lower(p.name), '" + ACENTOS + "', '" + SIN_ACENTOS + "') as String) LIKE :texto "
            + "OR lower(p.sku) LIKE :texto)",
           countQuery = "SELECT count(p) FROM Product p WHERE p.companyId = :companyId AND ("
            + "cast(function('translate', lower(p.name), '" + ACENTOS + "', '" + SIN_ACENTOS + "') as String) LIKE :texto "
            + "OR lower(p.sku) LIKE :texto)")
    Page<Product> buscar(Long companyId, String texto, Pageable pageable);

    @Query(value = "SELECT p FROM Product p WHERE p.companyId = :companyId AND p.itemType = :itemType AND ("
            + "cast(function('translate', lower(p.name), '" + ACENTOS + "', '" + SIN_ACENTOS + "') as String) LIKE :texto "
            + "OR lower(p.sku) LIKE :texto)",
           countQuery = "SELECT count(p) FROM Product p WHERE p.companyId = :companyId AND p.itemType = :itemType AND ("
            + "cast(function('translate', lower(p.name), '" + ACENTOS + "', '" + SIN_ACENTOS + "') as String) LIKE :texto "
            + "OR lower(p.sku) LIKE :texto)")
    Page<Product> buscarPorTipo(Long companyId, String itemType, String texto, Pageable pageable);

    /** Pares para translate(): cada vocal con tilde o diéresis (y la ñ) y su letra simple. */
    String ACENTOS = "áéíóúüàèìòùâêîôûñ";
    String SIN_ACENTOS = "aeiouuaeiouaeioun";

    Optional<Product> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Product> findBySkuAndCompanyId(String sku, Long companyId);
}
