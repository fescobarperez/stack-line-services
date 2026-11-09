package com.erp_maya.salud.controller;

import com.erp_maya.salud.dto.ClinicalDtos;
import com.erp_maya.salud.service.ClinicalService;
import com.erp_maya.salud.service.RecetaPdfService;
import com.erp_maya.salud.service.SaludAccess;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Atención clínica de Stackline Salud: consultas, casos, controles, catálogo
 * CIE-10 y receta en PDF. Todo pide el permiso `clinical_record` (lo valida
 * ClinicalService).
 */
@Controller("/api/salud")
public class ClinicalController {

    private final ClinicalService service;
    private final RecetaPdfService receta;

    public ClinicalController(ClinicalService service, RecetaPdfService receta) {
        this.service = service;
        this.receta = receta;
    }

    /** Abre la consulta de la cita (o devuelve la que ya tiene). */
    @Post("/appointments/{id}/encounter")
    public ClinicalDtos.EncounterView open(Long id, Authentication auth) {
        return service.openForAppointment(SaludAccess.of(auth), id);
    }

    @Get("/encounters/{id}")
    public ClinicalDtos.EncounterView get(Long id, Authentication auth) {
        return service.get(SaludAccess.of(auth), id);
    }

    /** Guarda el borrador. */
    @Put("/encounters/{id}")
    public ClinicalDtos.EncounterView save(Long id, @Valid @Body ClinicalDtos.EncounterRequest request, Authentication auth) {
        return service.save(SaludAccess.of(auth), id, request);
    }

    /** Guarda y firma: desde aquí la consulta solo admite adendas. */
    @Post("/encounters/{id}/sign")
    public ClinicalDtos.EncounterView sign(Long id, @Valid @Body ClinicalDtos.EncounterRequest request, Authentication auth) {
        return service.sign(SaludAccess.of(auth), id, request);
    }

    @Post("/encounters/{id}/addenda")
    public ClinicalDtos.EncounterView addendum(Long id, @Valid @Body ClinicalDtos.AddendumRequest request, Authentication auth) {
        return service.addAddendum(SaludAccess.of(auth), id, request);
    }

    @Get("/encounters/{id}/prescription.pdf")
    @Produces("application/pdf")
    public HttpResponse<byte[]> prescription(Long id, Authentication auth) {
        return HttpResponse.ok(receta.generar(SaludAccess.of(auth), id))
                .header("Content-Disposition", "inline; filename=\"Receta-" + id + ".pdf\"");
    }

    /** Historia clínica: casos con sus consultas y controles. */
    @Get("/patients/{id}/clinical-history")
    public ClinicalDtos.ClinicalHistory history(Long id, Authentication auth) {
        return service.history(SaludAccess.of(auth), id);
    }

    @Post("/cases/{id}/close")
    public ClinicalDtos.CaseView closeCase(Long id, @Valid @Body ClinicalDtos.CloseCaseRequest request, Authentication auth) {
        return service.closeCase(SaludAccess.of(auth), id, request);
    }

    @Post("/cases/{id}/reopen")
    public ClinicalDtos.CaseView reopenCase(Long id, Authentication auth) {
        return service.reopenCase(SaludAccess.of(auth), id);
    }

    @Post("/follow-ups/{id}/cancel")
    public ClinicalDtos.FollowUpView cancelFollowUp(Long id, Authentication auth) {
        return service.cancelFollowUp(SaludAccess.of(auth), id);
    }

    @Get("/cie10")
    public List<ClinicalDtos.Cie10View> cie10(@Nullable @QueryValue String q, Authentication auth) {
        return service.searchCie10(SaludAccess.of(auth), q);
    }
}
