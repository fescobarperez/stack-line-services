package com.erp_maya.salud.service;

import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Procesos periódicos de Salud: recordatorios de cita y de control. */
@Singleton
public class SaludJobs {

    private static final Logger log = LoggerFactory.getLogger(SaludJobs.class);

    private final AppointmentReminderService recordatorios;
    private final ControlReminderService controles;
    private final boolean activos;

    public SaludJobs(AppointmentReminderService recordatorios, ControlReminderService controles,
                     @Value("${erp.salud.reminders.enabled:true}") boolean activos) {
        this.recordatorios = recordatorios;
        this.controles = controles;
        this.activos = activos;
    }

    @Scheduled(fixedDelay = "5m", initialDelay = "1m")
    void recordatorios() {
        if (!activos) return;
        try {
            recordatorios.enviarPendientes();
        } catch (Exception e) {
            log.error("falló el ciclo de recordatorios de cita", e);
        }
    }

    /** Los controles se recuerdan con días de anticipación: basta revisar cada 15 minutos. */
    @Scheduled(fixedDelay = "15m", initialDelay = "3m")
    void controles() {
        if (!activos) return;
        try {
            controles.enviarPendientes();
        } catch (Exception e) {
            log.error("falló el ciclo de recordatorios de control", e);
        }
    }
}
