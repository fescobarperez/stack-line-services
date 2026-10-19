package com.erp_maya.catalog.repository;

import com.erp_maya.catalog.domain.Product;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /*
     * Las listas y búsquedas normales dejan fuera los productos con
     * review_status (propuestos por el asistente y sin aprobar, o
     * descartados): no se venden en el POS ni se ofrecen a otros clientes.
     */

    Page<Product> findByCompanyIdAndReviewStatusIsNull(Long companyId, Pageable pageable);

    Page<Product> findByCompanyIdAndNameContainsIgnoreCase(Long companyId, String name, Pageable pageable);

    Page<Product> findByCompanyIdAndItemTypeAndReviewStatusIsNull(Long companyId, String itemType, Pageable pageable);

    /** Los propuestos por el asistente en un estado de revisión (pending o discarded). */
    Page<Product> findByCompanyIdAndReviewStatus(Long companyId, String reviewStatus, Pageable pageable);

    long countByCompanyIdAndReviewStatus(Long companyId, String reviewStatus);

    /**
     * Mismo nombre sin importar mayúsculas: el asistente no propone dos
     * veces lo mismo, ni algo que ya existe en el catálogo.
     */
    @Query("SELECT p FROM Product p WHERE p.companyId = :companyId AND lower(p.name) = lower(:name) "
            + "AND (p.reviewStatus IS NULL OR p.reviewStatus = 'pending')")
    List<Product> mismoNombre(Long companyId, String name);

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
    @Query(value = "SELECT p FROM Product p WHERE p.companyId = :companyId AND p.reviewStatus IS NULL AND ("
            + "cast(function('translate', lower(p.name), '" + ACENTOS + "', '" + SIN_ACENTOS + "') as String) LIKE :texto "
            + "OR lower(p.sku) LIKE :texto)",
           countQuery = "SELECT count(p) FROM Product p WHERE p.companyId = :companyId AND p.reviewStatus IS NULL AND ("
            + "cast(function('translate', lower(p.name), '" + ACENTOS + "', '" + SIN_ACENTOS + "') as String) LIKE :texto "
            + "OR lower(p.sku) LIKE :texto)")
    Page<Product> buscar(Long companyId, String texto, Pageable pageable);

    @Query(value = "SELECT p FROM Product p WHERE p.companyId = :companyId AND p.itemType = :itemType AND p.reviewStatus IS NULL AND ("
            + "cast(function('translate', lower(p.name), '" + ACENTOS + "', '" + SIN_ACENTOS + "') as String) LIKE :texto "
            + "OR lower(p.sku) LIKE :texto)",
           countQuery = "SELECT count(p) FROM Product p WHERE p.companyId = :companyId AND p.itemType = :itemType AND p.reviewStatus IS NULL AND ("
            + "cast(function('translate', lower(p.name), '" + ACENTOS + "', '" + SIN_ACENTOS + "') as String) LIKE :texto "
            + "OR lower(p.sku) LIKE :texto)")
    Page<Product> buscarPorTipo(Long companyId, String itemType, String texto, Pageable pageable);

    /** Pares para translate(): cada vocal con tilde o diéresis (y la ñ) y su letra simple. */
    String ACENTOS = "áéíóúüàèìòùâêîôûñ";
    String SIN_ACENTOS = "aeiouuaeiouaeioun";

    Optional<Product> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Product> findBySkuAndCompanyId(String sku, Long companyId);
}
