package com.erp_maya.catalog.repository;

import com.erp_maya.catalog.domain.ProductSupplierPrice;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface ProductSupplierPriceRepository extends JpaRepository<ProductSupplierPrice, Long> {

    /** La vigente. Debería ser una sola; se devuelve lista por si quedaran huérfanas. */
    List<ProductSupplierPrice> findByCompanyIdAndProductSupplierIdAndValidUntilIsNull(
            Long companyId, Long productSupplierId);

    /** Historial completo, del precio más reciente al más viejo. */
    List<ProductSupplierPrice> findByCompanyIdAndProductSupplierIdOrderByValidFromDesc(
            Long companyId, Long productSupplierId);
}
