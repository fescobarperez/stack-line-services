package com.erp_maya.fel.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.fel.domain.FelDocument;
import com.erp_maya.fel.dto.FelDtos;
import com.erp_maya.fel.repository.FelDocumentRepository;
import com.erp_maya.pos.domain.Sale;
import com.erp_maya.pos.repository.SaleRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Facturación Electrónica. La certificación ante SAT está SIMULADA (genera UUID
 * y número de autorización locales); aquí se integraría el certificador real.
 */
@Singleton
public class FelService {

    private final FelDocumentRepository documents;
    private final SaleRepository sales;
    private final TenantContext tenant;

    public FelService(FelDocumentRepository documents, SaleRepository sales, TenantContext tenant) {
        this.documents = documents;
        this.sales = sales;
        this.tenant = tenant;
    }

    @Transactional
    public Page<FelDtos.Response> list(Pageable pageable) {
        return documents.findByCompanyIdOrderByIdDesc(tenant.getCompanyId(), pageable)
                .map(FelService::toResponse);
    }

    @Transactional
    public FelDtos.Response get(Long id) {
        return toResponse(documents.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("DTE " + id + " no encontrado")));
    }

    @Transactional
    public FelDtos.Response certify(FelDtos.CertifyRequest req) {
        Long companyId = tenant.getCompanyId();
        Sale sale = sales.findByIdAndCompanyId(req.saleId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Venta " + req.saleId() + " no encontrada"));

        FelDocument dte = new FelDocument();
        dte.setCompanyId(companyId);
        dte.setSale(sale);
        dte.setDteType(req.dteType() != null ? req.dteType() : "FACT");
        dte.setSeries(req.series() != null ? req.series() : "A");
        dte.setNumber(String.valueOf(Instant.now().toEpochMilli() % 1_000_000));
        dte.setReceptorName(sale.getClient() != null ? sale.getClient().getName() : "Consumidor Final");
        dte.setReceptorNit(sale.getClient() != null && sale.getClient().getNit() != null
                ? sale.getClient().getNit() : "CF");
        dte.setTaxableAmount(sale.getSubtotal() != null ? sale.getSubtotal() : BigDecimal.ZERO);
        dte.setExemptAmount(BigDecimal.ZERO);
        dte.setTax(sale.getTax() != null ? sale.getTax() : BigDecimal.ZERO);
        dte.setTotal(sale.getTotal() != null ? sale.getTotal() : BigDecimal.ZERO);
        // Simulación de la respuesta del certificador SAT.
        dte.setUuid(UUID.randomUUID().toString());
        dte.setAuthorizationNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        dte.setStatus("autorizado");
        dte.setIssuedAt(Instant.now());
        dte.setCertifiedAt(Instant.now());
        return toResponse(documents.save(dte));
    }

    private static FelDtos.Response toResponse(FelDocument d) {
        return new FelDtos.Response(d.getId(),
                d.getSale() != null ? d.getSale().getId() : null,
                d.getDteType(), d.getSeries(), d.getNumber(), d.getUuid(), d.getAuthorizationNumber(),
                d.getReceptorName(), d.getReceptorNit(), d.getTaxableAmount(), d.getExemptAmount(),
                d.getTax(), d.getTotal(), d.getStatus(), d.getIssuedAt(), d.getCertifiedAt());
    }
}
