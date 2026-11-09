package com.erp_maya.salud.service;

import com.erp_maya.salud.domain.ControlReminderCandidate;
import com.erp_maya.salud.domain.FollowUp;
import com.erp_maya.salud.repository.ControlReminderCandidateRepository;
import com.erp_maya.salud.repository.FollowUpRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recordatorio de control por WhatsApp: unos días antes de la fecha límite
 * TINO le escribe al paciente para preguntarle cómo sigue (mejor, igual,
 * peor) y ofrecerle agendar el control. Lo que conteste queda en el control,
 * dentro de su caso (FollowUpService.feedback).
 *
 * Mismo circuito que el de citas (AppointmentReminderService): el ERP decide
 * qué controles tocan (vista salud_v_control_reminders_due) y agents-services
 * escribe. El mensaje no lleva el diagnóstico ni el motivo del caso: viaja
 * por WhatsApp y puede leerlo otra persona.
 */
@Singleton
public class ControlReminderService {

    private static final Logger log = LoggerFactory.getLogger(ControlReminderService.class);
    static final String RUTA = "/v1/notifications/follow-up";
    static final String KIND = "recordatorio_control";
    private static final int LOTE = 30;

    private final ControlReminderCandidateRepository candidatas;
    private final FollowUpRepository followUps;
    private final HttpClient agente;
    private final String apiKey;

    public ControlReminderService(ControlReminderCandidateRepository candidatas, FollowUpRepository followUps,
                                  @Client(id = "agent") HttpClient agente,
                                  @Value("${erp.agent.api-key:}") String apiKey) {
        this.candidatas = candidatas;
        this.followUps = followUps;
        this.agente = agente;
        this.apiKey = apiKey;
    }

    public int enviarPendientes() {
        if (apiKey == null || apiKey.isBlank()) return 0;
        int enviados = 0;
        for (ControlReminderCandidate c : lote()) {
            if (entregar(c)) enviados++;
        }
        if (enviados > 0) log.info("{} recordatorio(s) de control entregado(s)", enviados);
        return enviados;
    }

    @Transactional
    List<ControlReminderCandidate> lote() {
        return candidatas.due(LOTE);
    }

    private boolean entregar(ControlReminderCandidate c) {
        try {
            var peticion = HttpRequest.POST(RUTA, cuerpo(c))
                    .header("X-Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON_TYPE);
            agente.toBlocking().exchange(peticion, String.class);
            registrar(c.getId(), true, null);
            return true;
        } catch (Exception e) {
            String motivo = e instanceof HttpClientResponseException r
                    ? r.getStatus().getCode() + " " + r.getResponse().getBody(String.class).orElse("")
                    : e.getClass().getSimpleName() + " " + e.getMessage();
            log.warn("recordatorio del control {} no entregado (intento {}): {}", c.getId(), c.getReminderAttempts() + 1, motivo);
            registrar(c.getId(), false, motivo);
            return false;
        }
    }

    @Transactional
    void registrar(Long followUpId, boolean ok, String error) {
        followUps.findById(followUpId).ifPresent(f -> {
            anotar(f, ok, error, Instant.now());
            followUps.update(f);
        });
    }

    /** Enviado; o, si falló, cuándo reintentar, hasta darlo por «fallido» (mismas esperas que el de citas). */
    static void anotar(FollowUp f, boolean ok, String error, Instant ahora) {
        f.setReminderAttempts(f.getReminderAttempts() + 1);
        if (ok) {
            f.setReminderStatus("enviado");
            f.setReminderSentAt(ahora);
            f.setReminderError(null);
            f.setReminderNextAt(null);
            return;
        }
        f.setReminderError(error == null ? null : error.length() > 500 ? error.substring(0, 500) : error);
        if (f.getReminderAttempts() >= AgendaRules.RECORDATORIO_INTENTOS) {
            f.setReminderStatus("fallido");
            f.setReminderNextAt(null);
        } else {
            f.setReminderNextAt(ahora.plus(AgendaRules.reminderBackoff(f.getReminderAttempts())));
        }
    }

    /** Lo que recibe agents-services; la fecha ya va escrita («viernes 13 de noviembre»). */
    static Map<String, Object> cuerpo(ControlReminderCandidate c) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("followUpId", c.getId());
        payload.put("patientId", c.getPatientId());
        payload.put("patientName", c.getPatientName());
        payload.put("phone", c.getPatientPhone());
        payload.put("companyName", c.getCompanyName());
        payload.put("professionalId", c.getProfessionalId());
        payload.put("professionalName", c.getProfessionalName());
        payload.put("typeId", c.getTypeId());
        payload.put("typeName", c.getTypeName());
        payload.put("dueOn", c.getDueOn().toString());
        payload.put("date", AgendaRules.fechaLarga(c.getDueOn().atTime(LocalTime.NOON).atZone(AgendaRules.GUATEMALA).toInstant()));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("notification_id", "control-" + c.getId());
        cuerpo.put("tenant_id", c.getCompanyId());
        cuerpo.put("kind", KIND);
        cuerpo.put("payload", payload);
        return cuerpo;
    }
}
