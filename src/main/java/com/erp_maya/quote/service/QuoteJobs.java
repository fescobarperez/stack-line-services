package com.erp_maya.quote.service;

import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Procesos periódicos de cotizaciones: avisos al cliente y limpieza de abiertas. */
@Singleton
public class QuoteJobs {

    private static final Logger log = LoggerFactory.getLogger(QuoteJobs.class);

    private final QuoteNotificationService servicio;
    private final boolean avisosActivos;
    private final long horasAbandono;

    public QuoteJobs(QuoteNotificationService servicio,
                     @Value("${erp.quotes.notifications.enabled:true}") boolean avisosActivos,
                     @Value("${erp.quotes.abandon-after-hours:72}") long horasAbandono) {
        this.servicio = servicio;
        this.avisosActivos = avisosActivos;
        this.horasAbandono = horasAbandono;
    }

    @Scheduled(fixedDelay = "15s", initialDelay = "30s")
    void avisos() {
        if (!avisosActivos) return;
        try {
            servicio.entregarPendientes();
        } catch (Exception e) {
            log.error("falló el ciclo de avisos de cotizaciones", e);
        }
    }

    @Scheduled(fixedDelay = "30m", initialDelay = "2m")
    void abandonadas() {
        if (horasAbandono <= 0) return;
        try {
            servicio.abandonarInactivas(horasAbandono);
        } catch (Exception e) {
            log.error("falló la limpieza de cotizaciones abiertas", e);
        }
    }
}
