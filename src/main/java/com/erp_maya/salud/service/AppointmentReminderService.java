package com.erp_maya.salud.service;

import com.erp_maya.salud.domain.Appointment;
import com.erp_maya.salud.domain.ReminderCandidate;
import com.erp_maya.salud.repository.AppointmentRepository;
import com.erp_maya.salud.repository.ReminderCandidateRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recordatorios de cita por WhatsApp.
 *
 * El ERP decide QUÉ citas recordar (vista salud_v_reminders_due) y se las
 * entrega a agents-services, que sabe por qué número escribe cada empresa y
 * manda el mensaje con botones para confirmar, reprogramar o cancelar. Corre
 * sin request (lo invoca SaludJobs), así que no usa TenantContext: cada
 * candidata trae su empresa.
 *
 * Si la entrega falla se reintenta con espera creciente; tras
 * {@link AgendaRules#RECORDATORIO_INTENTOS} intentos queda «fallido» y la
 * agenda lo muestra para que recepción llame.
 */
@Singleton
public class AppointmentReminderService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderService.class);
    static final String RUTA = "/v1/notifications/appointment";
    static final String KIND = "recordatorio_cita";
    private static final int LOTE = 30;

    private final ReminderCandidateRepository candidatas;
    private final AppointmentRepository appointments;
    private final HttpClient agente;
    private final String apiKey;

    public AppointmentReminderService(ReminderCandidateRepository candidatas, AppointmentRepository appointments,
                                      @Client(id = "agent") HttpClient agente,
                                      @Value("${erp.agent.api-key:}") String apiKey) {
        this.candidatas = candidatas;
        this.appointments = appointments;
        this.agente = agente;
        this.apiKey = apiKey;
    }

    /** Un ciclo: entrega los recordatorios que tocan ahora. Devuelve cuántos salieron. */
    public int enviarPendientes() {
        if (apiKey == null || apiKey.isBlank()) return 0;
        int enviados = 0;
        for (ReminderCandidate c : lote()) {
            if (entregar(c)) enviados++;
        }
        if (enviados > 0) log.info("{} recordatorio(s) de cita entregado(s)", enviados);
        return enviados;
    }

    @Transactional
    List<ReminderCandidate> lote() {
        return candidatas.due(LOTE);
    }

    private boolean entregar(ReminderCandidate c) {
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
            log.warn("recordatorio de la cita {} no entregado (intento {}): {}", c.getId(), c.getReminderAttempts() + 1, motivo);
            registrar(c.getId(), false, motivo);
            return false;
        }
    }

    /** Anota el resultado en la cita. */
    @Transactional
    void registrar(Long appointmentId, boolean ok, String error) {
        appointments.findById(appointmentId).ifPresent(a -> {
            anotar(a, ok, error, Instant.now());
            appointments.update(a);
        });
    }

    /** Enviado; o, si falló, cuándo reintentar, hasta darlo por «fallido». */
    static void anotar(Appointment a, boolean ok, String error, Instant ahora) {
        a.setReminderAttempts(a.getReminderAttempts() + 1);
        if (ok) {
            a.setReminderStatus("enviado");
            a.setReminderSentAt(ahora);
            a.setReminderError(null);
            a.setReminderNextAt(null);
            return;
        }
        a.setReminderError(error == null ? null : error.length() > 500 ? error.substring(0, 500) : error);
        if (a.getReminderAttempts() >= AgendaRules.RECORDATORIO_INTENTOS) {
            a.setReminderStatus("fallido");
            a.setReminderNextAt(null);
        } else {
            a.setReminderNextAt(ahora.plus(AgendaRules.reminderBackoff(a.getReminderAttempts())));
        }
    }

    /**
     * Lo que recibe agents-services. Los textos de fecha ya van armados en
     * hora de Guatemala: el mensaje al paciente no depende de que el otro
     * servicio sepa de zonas horarias.
     */
    static Map<String, Object> cuerpo(ReminderCandidate c) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", c.getId());
        payload.put("patientId", c.getPatientId());
        payload.put("patientName", c.getPatientName());
        payload.put("phone", c.getPatientPhone());
        payload.put("companyName", c.getCompanyName());
        payload.put("professionalName", c.getProfessionalName());
        payload.put("typeName", c.getTypeName());
        payload.put("resourceName", c.getResourceName());
        payload.put("startsAt", c.getStartsAt().toString());
        payload.put("date", AgendaRules.fechaLarga(c.getStartsAt()));
        payload.put("time", AgendaRules.hora(c.getStartsAt()));
        payload.put("status", c.getStatus());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        // Una por cita y hora: si se reprograma, la llave cambia y el nuevo recordatorio sale.
        cuerpo.put("notification_id", "cita-" + c.getId() + "-" + c.getStartsAt().getEpochSecond());
        cuerpo.put("tenant_id", c.getCompanyId());
        cuerpo.put("kind", KIND);
        cuerpo.put("payload", payload);
        return cuerpo;
    }
}
