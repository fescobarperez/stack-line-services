package com.erp_maya.platform.service;

import com.erp_maya.platform.dto.PlatformDtos.PlatformError;
import com.erp_maya.platform.security.PlatformException;
import io.micronaut.core.type.Argument;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reenvío de la consola a la API de administración de agents-services
 * ({@code /v1/admin/*}) con la llave {@code AGENT_KEY_PLATFORM}. El rol del
 * operador lo aplica quien llama; las validaciones y los errores
 * ({@code code}, {@code message}, {@code fields}) los pone agents-services y
 * pasan tal cual.
 */
@Singleton
public class AgentAdminProxy {

    private static final Logger log = LoggerFactory.getLogger(AgentAdminProxy.class);

    private final HttpClient agente;
    private final String adminKey;

    public AgentAdminProxy(@Client(id = "agent") HttpClient agente,
                           @Value("${erp.agent.admin-key:}") String adminKey) {
        this.agente = agente;
        this.adminKey = adminKey;
    }

    public MutableHttpResponse<?> forward(HttpMethod metodo, String ruta, @Nullable String body, Authentication auth) {
        if (adminKey == null || adminKey.isBlank()) {
            log.warn("AGENT_KEY_PLATFORM no está definida: la consola no puede administrar la IA");
            return error(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                    "La administración de IA no está configurada en este ambiente (falta AGENT_KEY_PLATFORM).");
        }
        MutableHttpRequest<String> peticion = HttpRequest.<String>create(metodo, ruta)
                .header("X-Api-Key", adminKey)
                .accept(MediaType.APPLICATION_JSON_TYPE);
        Object nombre = auth.getAttributes().get("name");
        if (nombre != null) {
            // Firma versiones de prompts, instrucciones y playbooks; va codificado porque lleva tildes.
            peticion.header("X-Operator-Name", URLEncoder.encode(nombre.toString(), StandardCharsets.UTF_8));
        }
        if (body != null) peticion.body(body).contentType(MediaType.APPLICATION_JSON_TYPE);
        try {
            HttpResponse<String> r = agente.toBlocking().exchange(peticion, String.class);
            return HttpResponse.status(r.getStatus())
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .body(r.getBody().orElse("{}"));
        } catch (HttpClientResponseException e) {
            int status = e.getStatus().getCode();
            if (status == 401 || status == 403) {
                // Credencial de servidor mal puesta, no sesión vencida: un 401
                // tal cual sacaría al operador de la consola.
                log.error("agents-services rechazó AGENT_KEY_PLATFORM ({})", status);
                return error(HttpStatus.BAD_GATEWAY, "AI_AUTH", "agents-services rechazó la llave de la consola.");
            }
            return HttpResponse.status(e.getStatus())
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .body(e.getResponse().getBody(String.class).orElse("{}"));
        } catch (RuntimeException e) {
            log.error("No se pudo contactar a agents-services: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE", "No se pudo contactar a agents-services.");
        }
    }

    /**
     * GET con el JSON ya leído, para combinarlo con datos del ERP (uso y
     * costos). Los fallos salen como {@link PlatformException} con los mismos
     * códigos que {@link #forward}.
     */
    public Map<String, Object> getJson(String ruta) {
        if (adminKey == null || adminKey.isBlank()) {
            throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                    "La administración de IA no está configurada en este ambiente (falta AGENT_KEY_PLATFORM).", null);
        }
        MutableHttpRequest<?> peticion = HttpRequest.GET(ruta)
                .header("X-Api-Key", adminKey)
                .accept(MediaType.APPLICATION_JSON_TYPE);
        try {
            Map<String, Object> r = agente.toBlocking().retrieve(peticion, Argument.mapOf(String.class, Object.class));
            return r == null ? Map.of() : r;
        } catch (HttpClientResponseException e) {
            int status = e.getStatus().getCode();
            if (status == 401 || status == 403) {
                log.error("agents-services rechazó AGENT_KEY_PLATFORM ({})", status);
                throw new PlatformException(HttpStatus.BAD_GATEWAY, "AI_AUTH", "agents-services rechazó la llave de la consola.", null);
            }
            log.error("agents-services respondió {} en {}", status, ruta);
            throw new PlatformException(HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE", "agents-services no devolvió el uso de IA.", null);
        } catch (RuntimeException e) {
            log.error("No se pudo contactar a agents-services: {}", e.getMessage());
            throw new PlatformException(HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE", "No se pudo contactar a agents-services.", null);
        }
    }

    private static MutableHttpResponse<PlatformError> error(HttpStatus status, String code, String mensaje) {
        return HttpResponse.<PlatformError>status(status).body(PlatformError.of(code, mensaje));
    }
}
