package com.erp_maya.platform.controller;

import com.erp_maya.platform.domain.PlatformOperator;
import com.erp_maya.platform.security.PlatformAccess;
import com.erp_maya.platform.service.AgentAdminProxy;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

/**
 * IA global en la consola (API.md · «IA»): proveedores, modelos, identidad de
 * Tino y tipos de agente. Leer: cualquier operador; escribir: owner. Los datos
 * viven en agents-services ({@code /v1/admin/ai/*}).
 */
@Controller("/api/platform/ai")
@Secured(SecurityRule.IS_AUTHENTICATED)
@ExecuteOn(TaskExecutors.BLOCKING)
public class PlatformAiController {

    private static final String ADMIN = "/v1/admin/ai";

    private final AgentAdminProxy proxy;

    public PlatformAiController(AgentAdminProxy proxy) {
        this.proxy = proxy;
    }

    private MutableHttpResponse<?> leer(String ruta, Authentication auth) {
        return proxy.forward(HttpMethod.GET, ADMIN + ruta, null, auth);
    }

    private MutableHttpResponse<?> escribir(HttpMethod metodo, String ruta, String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(metodo, ADMIN + ruta, body, auth);
    }

    @Get("/catalog")
    public MutableHttpResponse<?> catalog(Authentication auth) { return leer("/catalog", auth); }

    @Post(value = "/providers", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> createProvider(@Body String body, Authentication auth) {
        return escribir(HttpMethod.POST, "/providers", body, auth);
    }

    @Patch(value = "/providers/{id}", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> updateProvider(@PathVariable Long id, @Body String body, Authentication auth) {
        return escribir(HttpMethod.PATCH, "/providers/" + id, body, auth);
    }

    @Post(value = "/models", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> createModel(@Body String body, Authentication auth) {
        return escribir(HttpMethod.POST, "/models", body, auth);
    }

    @Patch(value = "/models/{id}", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> updateModel(@PathVariable Long id, @Body String body, Authentication auth) {
        return escribir(HttpMethod.PATCH, "/models/" + id, body, auth);
    }

    @Get("/identity")
    public MutableHttpResponse<?> identity(Authentication auth) { return leer("/identity", auth); }

    @Post(value = "/identity", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> createIdentity(@Body String body, Authentication auth) {
        return escribir(HttpMethod.POST, "/identity", body, auth);
    }

    @Post("/identity/{promptId}/activate")
    public MutableHttpResponse<?> activateIdentity(@PathVariable Long promptId, Authentication auth) {
        return escribir(HttpMethod.POST, "/identity/" + promptId + "/activate", "{}", auth);
    }

    @Get("/agents")
    public MutableHttpResponse<?> agents(Authentication auth) { return leer("/agents", auth); }

    @Post(value = "/agents", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> createAgent(@Body String body, Authentication auth) {
        return escribir(HttpMethod.POST, "/agents", body, auth);
    }

    @Patch(value = "/agents/{id}", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> updateAgent(@PathVariable Long id, @Body String body, Authentication auth) {
        return escribir(HttpMethod.PATCH, "/agents/" + id, body, auth);
    }

    @Post("/agents/{id}/archive")
    public MutableHttpResponse<?> archiveAgent(@PathVariable Long id, Authentication auth) {
        return escribir(HttpMethod.POST, "/agents/" + id + "/archive", "{}", auth);
    }

    @Post("/agents/{id}/restore")
    public MutableHttpResponse<?> restoreAgent(@PathVariable Long id, Authentication auth) {
        return escribir(HttpMethod.POST, "/agents/" + id + "/restore", "{}", auth);
    }

    @Get("/agents/{id}/prompts")
    public MutableHttpResponse<?> prompts(@PathVariable Long id, Authentication auth) {
        return leer("/agents/" + id + "/prompts", auth);
    }

    @Post(value = "/agents/{id}/prompts", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> createPrompt(@PathVariable Long id, @Body String body, Authentication auth) {
        return escribir(HttpMethod.POST, "/agents/" + id + "/prompts", body, auth);
    }

    @Post("/agents/{id}/prompts/{promptId}/activate")
    public MutableHttpResponse<?> activatePrompt(@PathVariable Long id, @PathVariable Long promptId, Authentication auth) {
        return escribir(HttpMethod.POST, "/agents/" + id + "/prompts/" + promptId + "/activate", "{}", auth);
    }
}
