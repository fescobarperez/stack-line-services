package com.erp_maya.quote.service;

import com.erp_maya.quote.domain.QuoteHistory;
import com.erp_maya.quote.domain.QuoteNotification;
import com.erp_maya.quote.repository.QuoteHistoryRepository;
import com.erp_maya.quote.repository.QuoteNotificationRepository;
import com.erp_maya.quote.repository.QuoteRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entrega a agents-services los avisos al cliente que dejó encolados el ERP
 * (bandeja {@code quote_notifications}) y cierra las cotizaciones abiertas que
 * nadie retomó. Lo invoca {@link QuoteJobs}; corre sin request, así que no usa
 * TenantContext: cada aviso ya trae su empresa.
 */
@Singleton
public class QuoteNotificationService {

    private static final Logger log = LoggerFactory.getLogger(QuoteNotificationService.class);
    private static final int MAX_INTENTOS = 8;
    private static final String RUTA = "/v1/notifications/quote";

    private final QuoteNotificationRepository avisos;
    private final QuoteRepository quotes;
    private final QuoteHistoryRepository history;
    private final HttpClient agente;
    private final String apiKey;
    private final ObjectMapper json;

    public QuoteNotificationService(QuoteNotificationRepository avisos, QuoteRepository quotes,
                                    QuoteHistoryRepository history, @Client(id = "agent") HttpClient agente,
                                    @Value("${erp.agent.api-key:}") String apiKey, ObjectMapper json) {
        this.avisos = avisos;
        this.quotes = quotes;
        this.history = history;
        this.agente = agente;
        this.apiKey = apiKey;
        this.json = json;
    }

    /** Un ciclo: intenta los avisos vencidos. Devuelve cuántos entregó. */
    public int entregarPendientes() {
        if (apiKey == null || apiKey.isBlank()) return 0;
        List<QuoteNotification> lote = avisos.findByStatusAndNextAttemptAtLessThanEqualsOrderByIdAsc(
                QuoteNotification.PENDIENTE, Instant.now(), Pageable.from(0, 20));
        int entregados = 0;
        for (QuoteNotification n : lote) {
            if (entregar(n)) entregados++;
        }
        return entregados;
    }

    private boolean entregar(QuoteNotification n) {
        n.setAttempts(n.getAttempts() + 1);
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("notification_id", n.getId());
            cuerpo.put("tenant_id", n.getCompanyId());
            cuerpo.put("kind", n.getKind());
            cuerpo.put("channel", n.getChannel());
            cuerpo.put("conversation_ref", n.getConversationRef());
            cuerpo.put("payload", json.readValue(n.getPayload(), Map.class));

            var peticion = HttpRequest.POST(RUTA, cuerpo)
                    .header("X-Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON_TYPE);
            agente.toBlocking().exchange(peticion, String.class);

            n.setStatus(QuoteNotification.ENVIADA);
            n.setSentAt(Instant.now());
            n.setLastError(null);
            avisos.update(n);
            history.save(new QuoteHistory(n.getCompanyId(), n.getQuoteId(),
                    "cotizacion_enviada".equals(n.getKind())
                            ? "Cotización enviada al cliente por WhatsApp"
                            : "Aviso de cambios enviado al cliente", null));
            return true;
        } catch (Exception e) {
            String motivo = e instanceof HttpClientResponseException r
                    ? r.getStatus().getCode() + " " + r.getResponse().getBody(String.class).orElse("")
                    : e.getClass().getSimpleName() + " " + e.getMessage();
            n.setLastError(motivo.length() > 500 ? motivo.substring(0, 500) : motivo);
            if (n.getAttempts() >= MAX_INTENTOS) {
                n.setStatus(QuoteNotification.FALLIDA);
                log.error("aviso {} de la cotización {} descartado tras {} intentos: {}",
                        n.getId(), n.getQuoteId(), n.getAttempts(), motivo);
                history.save(new QuoteHistory(n.getCompanyId(), n.getQuoteId(),
                        "Aviso al cliente NO enviado: " + n.getLastError(), null));
            } else {
                // Espera creciente: 1, 2, 4, 8… minutos.
                n.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(1L << (n.getAttempts() - 1))));
                log.warn("aviso {} no entregado (intento {}): {}", n.getId(), n.getAttempts(), motivo);
            }
            avisos.update(n);
            return false;
        }
    }

    /** abierta sin actividad → abandonada. */
    public int abandonarInactivas(long horas) {
        int n = quotes.updateAbandonarAntesDe(Instant.now().minus(Duration.ofHours(horas)));
        if (n > 0) log.info("{} cotización(es) abierta(s) sin actividad por {} h pasaron a abandonada", n, horas);
        return n;
    }
}
