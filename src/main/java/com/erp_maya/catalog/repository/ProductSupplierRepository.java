package com.erp_maya.catalog.repository;

import com.erp_maya.catalog.domain.ProductSupplier;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, Long> {

    List<ProductSupplier> findByCompanyIdAndProductIdOrderByPreferredDesc(Long companyId, Long productId);

    Optional<ProductSupplier> findByCompanyIdAndProductIdAndSupplierId(Long companyId, Long productId, Long supplierId);

    /** Todas las relaciones de la empresa: base del ranking de proveedores. */
    List<ProductSupplier> findByCompanyId(Long companyId);

    /** El otro lado de la relación: qué productos vende un proveedor. */
    List<ProductSupplier> findByCompanyIdAndSupplierIdOrderByProductId(Long companyId, Long supplierId);
}
