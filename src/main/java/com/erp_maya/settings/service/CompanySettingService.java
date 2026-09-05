package com.erp_maya.settings.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.settings.domain.CompanySetting;
import com.erp_maya.settings.dto.CompanySettingDtos;
import com.erp_maya.settings.repository.CompanySettingRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.List;

/** Preferencias por empresa (clave-valor con upsert por clave). */
@Singleton
public class CompanySettingService {

    private final CompanySettingRepository repository;
    private final TenantContext tenant;

    public CompanySettingService(CompanySettingRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Transactional
    public List<CompanySettingDtos.Response> list() {
        return repository.findByCompanyId(tenant.getCompanyId())
                .stream().map(CompanySettingService::toResponse).toList();
    }

    @Transactional
    public CompanySettingDtos.Response get(String key) {
        return toResponse(repository.findByCompanyIdAndSettingKey(tenant.getCompanyId(), key)
                .orElseThrow(() -> new ResourceNotFoundException("Ajuste '" + key + "' no encontrado")));
    }

    /** Crea o actualiza el ajuste con esa clave (upsert). */
    @Transactional
    public CompanySettingDtos.Response put(String key, CompanySettingDtos.Request req) {
        CompanySetting s = repository.findByCompanyIdAndSettingKey(tenant.getCompanyId(), key)
                .orElseGet(() -> {
                    CompanySetting n = new CompanySetting();
                    n.setCompanyId(tenant.getCompanyId());
                    n.setSettingKey(key);
                    return n;
                });
        s.setSettingValue(req.settingValue());
        s.setCategory(req.category());
        return toResponse(s.getId() == null ? repository.save(s) : repository.update(s));
    }

    @Transactional
    public void delete(String key) {
        repository.findByCompanyIdAndSettingKey(tenant.getCompanyId(), key).ifPresent(repository::delete);
    }

    private static CompanySettingDtos.Response toResponse(CompanySetting s) {
        return new CompanySettingDtos.Response(s.getId(), s.getSettingKey(), s.getSettingValue(), s.getCategory());
    }
}
