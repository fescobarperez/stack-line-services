package com.erp_maya.mail.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.mail.domain.CompanyMailSettings;
import com.erp_maya.mail.dto.MailSettingsDtos;
import com.erp_maya.mail.repository.CompanyMailSettingsRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.Set;

/** Lee y guarda el servidor de correo saliente del inquilino actual. */
@Singleton
public class MailSettingsService {

    private static final Set<String> SEGURIDAD = Set.of("none", "starttls", "ssl");

    private final CompanyMailSettingsRepository repo;
    private final TenantContext tenant;

    public MailSettingsService(CompanyMailSettingsRepository repo, TenantContext tenant) {
        this.repo = repo;
        this.tenant = tenant;
    }

    /**
     * Devuelve la configuración, o una vacía si la empresa no la tiene.
     *
     * Vacía y no 404: para la pantalla «todavía no configurado» y «configurado»
     * son el mismo formulario, y un error obligaría a tratar el caso normal
     * como excepción.
     */
    @Transactional
    public MailSettingsDtos.Response get() {
        return repo.findById(tenant.getCompanyId())
                .map(MailSettingsService::toResponse)
                .orElseGet(() -> new MailSettingsDtos.Response(
                        null, 587, null, false, null, null, "starttls", false));
    }

    @Transactional
    public MailSettingsDtos.Response save(MailSettingsDtos.Request req) {
        Long companyId = tenant.getCompanyId();
        CompanyMailSettings m = repo.findById(companyId).orElseGet(() -> {
            CompanyMailSettings nuevo = new CompanyMailSettings();
            nuevo.setCompanyId(companyId);
            return nuevo;
        });
        boolean esNuevo = m.getCreatedAt() == null;

        m.setHost(limpiar(req.host()));
        m.setPort(req.port() != null ? req.port() : 587);
        m.setUsername(limpiar(req.username()));
        m.setFromEmail(limpiar(req.fromEmail()));
        m.setFromName(limpiar(req.fromName()));

        String seg = req.security() == null ? "" : req.security().trim().toLowerCase();
        if (!seg.isBlank() && !SEGURIDAD.contains(seg)) {
            throw new IllegalStateException("El cifrado debe ser 'none', 'starttls' o 'ssl'.");
        }
        m.setSecurity(seg.isBlank() ? "starttls" : seg);

        // Contraseña: en blanco conserva la que había. Es lo que espera quien
        // solo viene a corregir el puerto y ve el campo vacío.
        if (Boolean.TRUE.equals(req.clearPassword())) {
            m.setPassword(null);
        } else if (req.password() != null && !req.password().isBlank()) {
            m.setPassword(req.password());
        }

        boolean activar = Boolean.TRUE.equals(req.enabled());
        if (activar && (m.getHost() == null || m.getFromEmail() == null)) {
            throw new IllegalStateException(
                    "Para activar el correo hacen falta al menos el host y el remitente.");
        }
        m.setEnabled(activar);

        return toResponse(esNuevo ? repo.save(m) : repo.update(m));
    }

    /** Uso interno del futuro envío: sí incluye la credencial. */
    @Transactional
    public CompanyMailSettings raw(Long companyId) {
        return repo.findById(companyId).orElse(null);
    }

    private static String limpiar(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static MailSettingsDtos.Response toResponse(CompanyMailSettings m) {
        return new MailSettingsDtos.Response(
                m.getHost(), m.getPort(), m.getUsername(),
                m.getPassword() != null && !m.getPassword().isBlank(),
                m.getFromEmail(), m.getFromName(), m.getSecurity(), m.getEnabled());
    }
}
