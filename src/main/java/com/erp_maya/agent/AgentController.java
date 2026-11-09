package com.erp_maya.agent;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.authentication.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Intermediario entre el widget del asistente y agents-services.
 *
 * El navegador nunca tiene la api-key del agente: aquí se valida el JWT de la
 * sesión, se arma el turno con la empresa y el usuario del token (no del
 * cuerpo) y se reenvía a {@code POST /v1/agent/turn} con {@code X-Api-Key}.
 *
 * Del cuerpo del front solo se toman: la clave de la conversación, el input,
 * las capacidades y la llave de idempotencia.
 */
@Controller("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final Pattern CLAVE_VALIDA = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final int MAX_LLAVE = 100;
    private static final String CANAL = "erp";

    private final HttpClient agente;
    private final String apiKey;

    public AgentController(@Client(id = "agent") HttpClient agente,
                           @Value("${erp.agent.api-key:}") String apiKey) {
        this.agente = agente;
        this.apiKey = apiKey;
    }

    @Post(value = "/turn", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.BLOCKING)
    public MutableHttpResponse<?> turn(@Body Map<String, Object> cuerpo, @Nullable Authentication auth,
                                      HttpRequest<?> entrante) {
        Long userId = numero(auth, "userId");
        Long companyId = numero(auth, "companyId");
        if (userId == null || companyId == null) {
            return error(HttpStatus.FORBIDDEN, "La sesión no trae usuario o empresa.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AGENT_KEY_ERP no está definida: el asistente no puede atender");
            return error(HttpStatus.SERVICE_UNAVAILABLE, "El asistente no está configurado en este ambiente.");
        }

        String clave = texto(mapa(cuerpo.get("conversation_ref")).get("external_id"));
        String llave = texto(cuerpo.get("idempotency_key"));
        Map<String, Object> input = mapa(cuerpo.get("input"));
        if (clave == null || !CLAVE_VALIDA.matcher(clave).matches()) {
            return error(HttpStatus.BAD_REQUEST, "Falta la clave de la conversación o no es válida.");
        }
        if (llave == null || llave.isBlank() || llave.length() > MAX_LLAVE) {
            return error(HttpStatus.BAD_REQUEST, "Falta la llave de idempotencia o no es válida.");
        }
        if (texto(input.get("type")) == null) {
            return error(HttpStatus.BAD_REQUEST, "Falta el tipo de input.");
        }

        Map<String, Object> ref = new LinkedHashMap<>();
        // La conversación es del usuario: dos usuarios con la misma clave no
        // comparten hilo, y un usuario no puede escribir en el de otro.
        ref.put("external_id", "u" + userId + ":" + clave);
        // Cada empresa es una cuenta del canal erp (channels.account_ref).
        ref.put("account", CANAL + ":" + companyId);

        Map<String, Object> turno = new LinkedHashMap<>();
        turno.put("channel", CANAL);
        turno.put("conversation_ref", ref);
        turno.put("actor", Map.of("type", "user", "user_id", userId));
        turno.put("input", input);
        if (cuerpo.get("capabilities") instanceof Map<?, ?> caps) turno.put("capabilities", caps);
        turno.put("idempotency_key", "erp:" + userId + ":" + llave);

        // El agente consulta el ERP con el token de ESTE usuario: el ERP aplica
        // su empresa y sus permisos. agents-services lo usa solo durante el
        // turno y no lo guarda.
        String tokenUsuario = entrante.getHeaders().getAuthorization()
                .filter(h -> h.regionMatches(true, 0, "Bearer ", 0, 7))
                .map(h -> h.substring(7).trim())
                .orElse(null);

        MutableHttpRequest<?> peticion = HttpRequest.POST("/v1/agent/turn", turno)
                .header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE);
        if (tokenUsuario != null) peticion.header("X-Erp-User-Token", tokenUsuario);
        try {
            HttpResponse<String> r = agente.toBlocking().exchange(peticion, String.class);
            return HttpResponse.status(r.getStatus())
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .body(r.getBody().orElse("{}"));
        } catch (HttpClientResponseException e) {
            int status = e.getStatus().getCode();
            if (status == 401 || status == 403) {
                // Un 401 del agente es credencial de servidor mal puesta, no
                // sesión vencida: devolverlo tal cual cerraría la sesión del usuario.
                log.error("agents-services rechazó la api-key del ERP ({})", status);
                return error(HttpStatus.BAD_GATEWAY, "El asistente rechazó la credencial del ERP.");
            }
            log.warn("agents-services respondió {} para empresa={} usuario={}", status, companyId, userId);
            return HttpResponse.status(e.getStatus())
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .body(e.getResponse().getBody(String.class).orElse("{}"));
        } catch (RuntimeException e) {
            log.error("No se pudo contactar a agents-services: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "No se pudo contactar al asistente.");
        }
    }

    private static MutableHttpResponse<Map<String, String>> error(HttpStatus status, String mensaje) {
        return HttpResponse.<Map<String, String>>status(status).body(Map.of("message", mensaje));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String texto(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static Long numero(Authentication auth, String claim) {
        if (auth == null) return null;
        Object v = auth.getAttributes().get(claim);
        if (v instanceof Number n) return n.longValue();
        try {
            return v == null ? null : Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
