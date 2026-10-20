package com.erp_maya.platform.controller;

import com.erp_maya.company.repository.CompanyRepository;
import com.erp_maya.platform.domain.PlatformOperator;
import com.erp_maya.platform.security.PlatformAccess;
import com.erp_maya.platform.security.PlatformException;
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
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Agentes de IA de una empresa en la consola (API.md · «Agentes de la
 * empresa»). Solo operadores de plataforma: el ERP de la empresa no los ve.
 *
 * Roles: leer, cualquiera; política, alta, edición, archivo y widgets web, owner;
 * instrucciones y playbook, owner o support. Se comprueba que la empresa
 * exista antes de reenviar a agents-services ({@code /v1/admin/tenants/{id}}).
 */
@Controller("/api/platform/companies/{companyId}/agents")
@Secured(SecurityRule.IS_AUTHENTICATED)
@ExecuteOn(TaskExecutors.BLOCKING)
public class PlatformCompanyAgentsController {

    private final AgentAdminProxy proxy;
    private final CompanyRepository companies;

    public PlatformCompanyAgentsController(AgentAdminProxy proxy, CompanyRepository companies) {
        this.proxy = proxy;
        this.companies = companies;
    }

    private String base(Long companyId) {
        if (!companies.existsById(companyId)) throw PlatformException.notFound("La empresa no existe");
        return "/v1/admin/tenants/" + companyId;
    }

    @Get
    public MutableHttpResponse<?> view(@PathVariable Long companyId, Authentication auth) {
        return proxy.forward(HttpMethod.GET, base(companyId), null, auth);
    }

    @Put(value = "/policy", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> policy(@PathVariable Long companyId, @Body String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.PUT, base(companyId) + "/policy", body, auth);
    }

    @Post(consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> create(@PathVariable Long companyId, @Body String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.POST, base(companyId) + "/agents", body, auth);
    }

    @Patch(value = "/{id}", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> update(@PathVariable Long companyId, @PathVariable Long id, @Body String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.PATCH, base(companyId) + "/agents/" + id, body, auth);
    }

    @Post("/{id}/archive")
    public MutableHttpResponse<?> archive(@PathVariable Long companyId, @PathVariable Long id, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.POST, base(companyId) + "/agents/" + id + "/archive", "{}", auth);
    }

    // ── WhatsApp (números de la cuenta de Stackline en Meta) ────────────

    @Get("/whatsapp/lookup")
    public MutableHttpResponse<?> lookupWhatsApp(@PathVariable Long companyId, @QueryValue String phoneNumberId, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.GET, base(companyId) + "/whatsapp/lookup?phoneNumberId="
                + URLEncoder.encode(phoneNumberId, StandardCharsets.UTF_8), null, auth);
    }

    @Post(value = "/whatsapp-channels", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> connectWhatsApp(@PathVariable Long companyId, @Body String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.POST, base(companyId) + "/whatsapp-channels", body, auth);
    }

    @Post("/whatsapp-channels/{channelId}/disconnect")
    public MutableHttpResponse<?> disconnectWhatsApp(@PathVariable Long companyId, @PathVariable Long channelId, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.POST, base(companyId) + "/whatsapp-channels/" + channelId + "/disconnect", "{}", auth);
    }

    // ── Canal web (widgets de chat en la página de la empresa) ──────────

    @Post(value = "/web-channels", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> createWebChannel(@PathVariable Long companyId, @Body String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.POST, base(companyId) + "/web-channels", body, auth);
    }

    @Patch(value = "/web-channels/{channelId}", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> updateWebChannel(@PathVariable Long companyId, @PathVariable Long channelId,
                                                   @Body String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER);
        return proxy.forward(HttpMethod.PATCH, base(companyId) + "/web-channels/" + channelId, body, auth);
    }

    @Get("/{id}/instructions")
    public MutableHttpResponse<?> instructions(@PathVariable Long companyId, @PathVariable Long id, Authentication auth) {
        return proxy.forward(HttpMethod.GET, base(companyId) + "/agents/" + id + "/instructions", null, auth);
    }

    @Post(value = "/{id}/instructions", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> createInstructions(@PathVariable Long companyId, @PathVariable Long id, @Body String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER, PlatformOperator.SUPPORT);
        return proxy.forward(HttpMethod.POST, base(companyId) + "/agents/" + id + "/instructions", body, auth);
    }

    @Post("/{id}/instructions/{versionId}/activate")
    public MutableHttpResponse<?> activateInstructions(@PathVariable Long companyId, @PathVariable Long id,
                                                       @PathVariable Long versionId, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER, PlatformOperator.SUPPORT);
        return proxy.forward(HttpMethod.POST, base(companyId) + "/agents/" + id + "/instructions/" + versionId + "/activate", "{}", auth);
    }

    @Post("/{id}/instructions/clear")
    public MutableHttpResponse<?> clearInstructions(@PathVariable Long companyId, @PathVariable Long id, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER, PlatformOperator.SUPPORT);
        return proxy.forward(HttpMethod.POST, base(companyId) + "/agents/" + id + "/instructions/clear", "{}", auth);
    }

    @Put(value = "/{id}/playbook", consumes = MediaType.APPLICATION_JSON)
    public MutableHttpResponse<?> playbook(@PathVariable Long companyId, @PathVariable Long id, @Body String body, Authentication auth) {
        PlatformAccess.require(auth, PlatformOperator.OWNER, PlatformOperator.SUPPORT);
        return proxy.forward(HttpMethod.PUT, base(companyId) + "/agents/" + id + "/playbook", body, auth);
    }
}
