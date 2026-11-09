package com.erp_maya.salud.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.salud.domain.SaludSettings;
import com.erp_maya.salud.dto.AgendaDtos;
import com.erp_maya.salud.repository.SaludSettingsRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import static com.erp_maya.salud.service.SaludAccess.AGENDA;
import static com.erp_maya.salud.service.SaludAccess.AGENDA_CONFIG;
import static com.erp_maya.salud.service.SaludAccess.EDITAR;
import static com.erp_maya.salud.service.SaludAccess.VER;

/** Ajustes de Salud de la empresa: recordatorios de cita y de control. */
@Singleton
public class SaludSettingsService {

    private final SaludSettingsRepository settings;
    private final TenantContext tenant;

    public SaludSettingsService(SaludSettingsRepository settings, TenantContext tenant) {
        this.settings = settings;
        this.tenant = tenant;
    }

    @Transactional
    public AgendaDtos.SettingsView get(SaludAccess.Actor actor) {
        actor.require(AGENDA, VER);
        return settings.findById(tenant.getCompanyId()).map(SaludSettingsService::toView)
                .orElse(new AgendaDtos.SettingsView(true, 24, true, 2));
    }

    @Transactional
    public AgendaDtos.SettingsView save(SaludAccess.Actor actor, AgendaDtos.SettingsRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        Long companyId = tenant.getCompanyId();
        SaludSettings s = settings.findById(companyId).orElse(null);
        boolean nuevo = s == null;
        if (nuevo) {
            s = new SaludSettings();
            s.setCompanyId(companyId);
        }
        s.setRemindersEnabled(req.remindersEnabled());
        s.setReminderHours(AgendaRules.reminderHours(req.reminderHours()));
        if (req.controlRemindersEnabled() != null) s.setControlRemindersEnabled(req.controlRemindersEnabled());
        if (req.controlReminderDays() != null) s.setControlReminderDays(CajaRules.controlReminderDays(req.controlReminderDays()));
        return toView(nuevo ? settings.save(s) : settings.update(s));
    }

    private static AgendaDtos.SettingsView toView(SaludSettings s) {
        return new AgendaDtos.SettingsView(s.isRemindersEnabled(), s.getReminderHours(),
                s.isControlRemindersEnabled(), s.getControlReminderDays());
    }
}
