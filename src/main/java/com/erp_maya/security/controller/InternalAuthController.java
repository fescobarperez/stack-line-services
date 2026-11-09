package com.erp_maya.security.controller;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.security.dto.ServiceTokenDtos;
import com.erp_maya.security.service.ServiceTokenService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.validation.Valid;

import java.util.Map;

/**
 * Endpoints de servicio a servicio (agents-services → ERP). No llevan JWT: se
 * autentican con la llave de servicio en {@code X-Api-Key}, que nunca llega al
 * navegador. Caddy no expone {@code /api/internal/**} hacia afuera.
 */
@Controller("/api/internal")
@Secured(SecurityRule.IS_ANONYMOUS)
public class InternalAuthController {

    private final ServiceTokenService service;

    public InternalAuthController(ServiceTokenService service) {
        this.service = service;
    }

    @Post("/service-token")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<?> serviceToken(@Nullable @Header("X-Api-Key") String apiKey,
                                        @Valid @Body ServiceTokenDtos.Request request) {
        if (!service.llaveValida(apiKey)) {
            return HttpResponse.unauthorized();
        }
        try {
            return HttpResponse.ok(service.emitir(request.companyId()));
        } catch (ResourceNotFoundException e) {
            return HttpResponse.notFound(Map.of("code", "COMPANY_NOT_FOUND"));
        } catch (ServiceTokenService.EmpresaNoHabilitadaException e) {
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(Map.of("code", "COMPANY_DISABLED"));
        }
    }
}
