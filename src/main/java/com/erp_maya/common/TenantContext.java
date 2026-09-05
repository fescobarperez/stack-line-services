package com.erp_maya.common;

import io.micronaut.runtime.http.scope.RequestScope;

/**
 * Contexto multi-empresa por request. Lo llena {@link TenantFilter} desde el
 * header X-Company-Id; los servicios lo leen para aislar los datos por inquilino.
 */
@RequestScope
public class TenantContext {

    private Long companyId;

    public Long getCompanyId() {
        if (companyId == null) {
            throw new MissingTenantException();
        }
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public boolean isPresent() {
        return companyId != null;
    }
}
