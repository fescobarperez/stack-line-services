package com.erp_maya.uom.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.uom.domain.UnitOfMeasure;
import com.erp_maya.uom.domain.UomConversion;
import com.erp_maya.uom.dto.UomDtos;
import com.erp_maya.uom.repository.UnitOfMeasureRepository;
import com.erp_maya.uom.repository.UomConversionRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

/** Unidades de medida y sus conversiones. */
@Singleton
public class UomService {

    private final UnitOfMeasureRepository units;
    private final UomConversionRepository conversions;
    private final TenantContext tenant;

    public UomService(UnitOfMeasureRepository units, UomConversionRepository conversions, TenantContext tenant) {
        this.units = units;
        this.conversions = conversions;
        this.tenant = tenant;
    }

    // ── Unidades ──────────────────────────────────────────────────────────
    @Transactional
    public List<UomDtos.UnitResponse> listUnits() {
        return units.findByCompanyId(tenant.getCompanyId()).stream().map(UomService::toUnit).toList();
    }

    @Transactional
    public UomDtos.UnitResponse createUnit(UomDtos.UnitRequest req) {
        UnitOfMeasure u = new UnitOfMeasure();
        u.setCompanyId(tenant.getCompanyId());
        applyUnit(u, req);
        return toUnit(units.save(u));
    }

    @Transactional
    public UomDtos.UnitResponse updateUnit(Long id, UomDtos.UnitRequest req) {
        UnitOfMeasure u = findUnit(id);
        applyUnit(u, req);
        return toUnit(units.update(u));
    }

    @Transactional
    public void deleteUnit(Long id) {
        units.delete(findUnit(id));
    }

    // ── Conversiones ──────────────────────────────────────────────────────
    @Transactional
    public List<UomDtos.ConversionResponse> listConversions() {
        return conversions.findByCompanyId(tenant.getCompanyId()).stream().map(UomService::toConversion).toList();
    }

    @Transactional
    public UomDtos.ConversionResponse createConversion(UomDtos.ConversionRequest req) {
        UomConversion c = new UomConversion();
        c.setCompanyId(tenant.getCompanyId());
        c.setFromUom(findUnit(req.fromUomId()));
        c.setToUom(findUnit(req.toUomId()));
        c.setFactor(req.factor());
        return toConversion(conversions.save(c));
    }

    @Transactional
    public void deleteConversion(Long id) {
        UomConversion c = conversions.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversión " + id + " no encontrada"));
        conversions.delete(c);
    }

    private void applyUnit(UnitOfMeasure u, UomDtos.UnitRequest req) {
        u.setCode(req.code());
        u.setName(req.name());
        u.setSymbol(req.symbol());
        u.setUomType(req.uomType());
        u.setIsBase(req.isBase() != null ? req.isBase() : Boolean.FALSE);
        u.setActive(req.active() != null ? req.active() : Boolean.TRUE);
    }

    private UnitOfMeasure findUnit(Long id) {
        return units.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad " + id + " no encontrada"));
    }

    private static UomDtos.UnitResponse toUnit(UnitOfMeasure u) {
        return new UomDtos.UnitResponse(u.getId(), u.getCode(), u.getName(), u.getSymbol(),
                u.getUomType(), u.getIsBase(), u.getActive());
    }

    private static UomDtos.ConversionResponse toConversion(UomConversion c) {
        return new UomDtos.ConversionResponse(c.getId(),
                c.getFromUom() != null ? c.getFromUom().getId() : null,
                c.getFromUom() != null ? c.getFromUom().getCode() : null,
                c.getToUom() != null ? c.getToUom().getId() : null,
                c.getToUom() != null ? c.getToUom().getCode() : null,
                c.getFactor());
    }
}
