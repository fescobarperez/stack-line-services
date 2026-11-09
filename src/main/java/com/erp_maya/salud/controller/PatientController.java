package com.erp_maya.salud.controller;

import com.erp_maya.salud.dto.PatientDtos;
import com.erp_maya.salud.service.PatientService;
import com.erp_maya.salud.service.SaludAccess;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;

/**
 * Pacientes de Stackline Salud. Todo bajo /api/salud, que el ModuleFilter
 * solo abre a las empresas con el módulo `salud` en su plan. Los permisos
 * (`patients`, `clinical_record`) los valida PatientService.
 */
@Controller("/api/salud/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @Get
    public PatientDtos.ListPage list(@Nullable @QueryValue String search, @Nullable @QueryValue String status,
                                     @QueryValue(defaultValue = "0") int page,
                                     @QueryValue(defaultValue = "20") int size,
                                     Authentication auth) {
        return service.list(SaludAccess.of(auth), search, status, page, size);
    }

    /** Buscar antes de crear: clientes que coinciden y si ya son pacientes. */
    @Get("/candidates")
    public List<PatientDtos.Candidate> candidates(@Nullable @QueryValue String q, Authentication auth) {
        return service.candidates(SaludAccess.of(auth), q);
    }

    /** `clientIds=1,2,3` → cuáles son pacientes (máximo 500). */
    @Get("/lookup")
    public List<PatientDtos.ClientLink> lookup(@Nullable @QueryValue String clientIds, Authentication auth) {
        return service.lookup(SaludAccess.of(auth), ids(clientIds));
    }

    @Get("/{id}")
    public PatientDtos.Detail get(Long id, Authentication auth) {
        return service.get(SaludAccess.of(auth), id);
    }

    /** Alta unificada: cliente y paciente en una sola operación. */
    @Post
    @Status(HttpStatus.CREATED)
    public PatientDtos.Detail create(@Valid @Body PatientDtos.CreateRequest request, Authentication auth) {
        return service.create(SaludAccess.of(auth), request);
    }

    /** Convierte en paciente a un cliente existente. */
    @Post("/from-client/{clientId}")
    @Status(HttpStatus.CREATED)
    public PatientDtos.Detail fromClient(Long clientId, @Valid @Body PatientDtos.FromClientRequest request,
                                         Authentication auth) {
        return service.fromClient(SaludAccess.of(auth), clientId, request);
    }

    @Put("/{id}")
    public PatientDtos.Detail update(Long id, @Valid @Body PatientDtos.UpdateRequest request, Authentication auth) {
        return service.update(SaludAccess.of(auth), id, request);
    }

    @Put("/{id}/clinical")
    public PatientDtos.Detail updateClinical(Long id, @Valid @Body PatientDtos.ClinicalData request,
                                             Authentication auth) {
        return service.updateClinical(SaludAccess.of(auth), id, request);
    }

    @Patch("/{id}/status")
    public PatientDtos.Detail status(Long id, @Valid @Body PatientDtos.StatusRequest request, Authentication auth) {
        return service.changeStatus(SaludAccess.of(auth), id, request);
    }

    @Post("/{id}/consents")
    public PatientDtos.Detail addConsent(Long id, @Valid @Body PatientDtos.ConsentRequest request,
                                         Authentication auth) {
        return service.addConsent(SaludAccess.of(auth), id, request);
    }

    @Post("/{id}/consents/{consentId}/revoke")
    public PatientDtos.Detail revokeConsent(Long id, Long consentId, Authentication auth) {
        return service.revokeConsent(SaludAccess.of(auth), id, consentId);
    }

    /** "1, 2,x,3" → [1, 2, 3]: lo que no es número se ignora. */
    static List<Long> ids(@Nullable String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Long> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            try {
                out.add(Long.valueOf(p.trim()));
            } catch (NumberFormatException ignored) {
                // un id mal formado no debe tumbar la lista entera
            }
        }
        return out;
    }

    /** Bitácora: quién abrió o cambió este expediente. */
    @Get("/{id}/access-log")
    public List<PatientDtos.AccessEntry> accessLog(Long id, @QueryValue(defaultValue = "0") int page,
                                                   @QueryValue(defaultValue = "50") int size,
                                                   Authentication auth) {
        return service.accessLog(SaludAccess.of(auth), id, page, size);
    }
}
