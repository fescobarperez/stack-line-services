package com.erp_maya.salud.controller;

import com.erp_maya.salud.dto.AgendaDtos;
import com.erp_maya.salud.service.AgendaConfigService;
import com.erp_maya.salud.service.AppointmentService;
import com.erp_maya.salud.service.SaludAccess;
import com.erp_maya.salud.service.SaludSettingsService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.Valid;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Agenda de Stackline Salud. Las fechas van en ISO-8601
 * (`from=2026-11-02T06:00:00Z`, `date=2026-11-02`). Los permisos (`agenda`,
 * `agenda_config`) los validan los servicios.
 */
@Controller("/api/salud")
public class AgendaController {

    private final AgendaConfigService config;
    private final AppointmentService appointments;
    private final SaludSettingsService settings;

    public AgendaController(AgendaConfigService config, AppointmentService appointments,
                            SaludSettingsService settings) {
        this.config = config;
        this.appointments = appointments;
        this.settings = settings;
    }

    // ── Ajustes ──────────────────────────────────────────────────────────

    @Get("/settings")
    public AgendaDtos.SettingsView settings(Authentication auth) {
        return settings.get(SaludAccess.of(auth));
    }

    @Put("/settings")
    public AgendaDtos.SettingsView saveSettings(@Valid @Body AgendaDtos.SettingsRequest request, Authentication auth) {
        return settings.save(SaludAccess.of(auth), request);
    }

    // ── Configuración ────────────────────────────────────────────────────

    @Get("/professionals")
    public List<AgendaDtos.ProfessionalView> professionals(Authentication auth) {
        return config.professionals(SaludAccess.of(auth));
    }

    @Put("/professionals/{userId}")
    public AgendaDtos.ProfessionalView saveProfessional(Long userId, @Valid @Body AgendaDtos.ProfessionalRequest request,
                                                        Authentication auth) {
        return config.saveProfessional(SaludAccess.of(auth), userId, request);
    }

    @Put("/professionals/{userId}/schedule")
    public AgendaDtos.ProfessionalView saveSchedule(Long userId, @Valid @Body AgendaDtos.ScheduleRequest request,
                                                    Authentication auth) {
        return config.saveSchedule(SaludAccess.of(auth), userId, request);
    }

    @Get("/resources")
    public List<AgendaDtos.ResourceView> resources(Authentication auth) {
        return config.resources(SaludAccess.of(auth));
    }

    @Post("/resources")
    @Status(HttpStatus.CREATED)
    public AgendaDtos.ResourceView createResource(@Valid @Body AgendaDtos.ResourceRequest request, Authentication auth) {
        return config.createResource(SaludAccess.of(auth), request);
    }

    @Put("/resources/{id}")
    public AgendaDtos.ResourceView updateResource(Long id, @Valid @Body AgendaDtos.ResourceRequest request,
                                                  Authentication auth) {
        return config.updateResource(SaludAccess.of(auth), id, request);
    }

    @Get("/appointment-types")
    public List<AgendaDtos.TypeView> types(Authentication auth) {
        return config.types(SaludAccess.of(auth));
    }

    @Post("/appointment-types")
    @Status(HttpStatus.CREATED)
    public AgendaDtos.TypeView createType(@Valid @Body AgendaDtos.TypeRequest request, Authentication auth) {
        return config.createType(SaludAccess.of(auth), request);
    }

    @Put("/appointment-types/{id}")
    public AgendaDtos.TypeView updateType(Long id, @Valid @Body AgendaDtos.TypeRequest request, Authentication auth) {
        return config.updateType(SaludAccess.of(auth), id, request);
    }

    // ── Citas ────────────────────────────────────────────────────────────

    @Get("/appointments")
    public List<AgendaDtos.AppointmentView> appointments(@QueryValue Instant from, @QueryValue Instant to,
                                                         @Nullable @QueryValue Long professionalId,
                                                         @Nullable @QueryValue Long resourceId,
                                                         @Nullable @QueryValue Long patientId,
                                                         @Nullable @QueryValue String status,
                                                         Authentication auth) {
        return appointments.list(SaludAccess.of(auth), from, to, professionalId, resourceId, patientId, status);
    }

    @Get("/appointments/{id}")
    public AgendaDtos.AppointmentView appointment(Long id, Authentication auth) {
        return appointments.get(SaludAccess.of(auth), id);
    }

    @Post("/appointments")
    @Status(HttpStatus.CREATED)
    public AgendaDtos.AppointmentView create(@Valid @Body AgendaDtos.AppointmentRequest request, Authentication auth) {
        return appointments.create(SaludAccess.of(auth), request);
    }

    @Put("/appointments/{id}/schedule")
    public AgendaDtos.AppointmentView reschedule(Long id, @Valid @Body AgendaDtos.RescheduleRequest request,
                                                 Authentication auth) {
        return appointments.reschedule(SaludAccess.of(auth), id, request);
    }

    @Post("/appointments/{id}/status")
    public AgendaDtos.AppointmentView status(Long id, @Valid @Body AgendaDtos.StatusRequest request, Authentication auth) {
        return appointments.changeStatus(SaludAccess.of(auth), id, request);
    }

    @Put("/appointments/{id}/notes")
    public AgendaDtos.AppointmentView notes(Long id, @Valid @Body AgendaDtos.NotesRequest request, Authentication auth) {
        return appointments.updateNotes(SaludAccess.of(auth), id, request);
    }

    /** Horarios libres: `professionalId`, `date` (2026-11-02), `typeId` y opcional `step` en minutos. */
    @Get("/availability")
    public AgendaDtos.Availability availability(@QueryValue Long professionalId, @QueryValue LocalDate date,
                                               @QueryValue Long typeId, @Nullable @QueryValue Integer step,
                                               Authentication auth) {
        return appointments.availability(SaludAccess.of(auth), professionalId, date, typeId, step);
    }

    // ── Bloqueos ─────────────────────────────────────────────────────────

    @Get("/blocks")
    public List<AgendaDtos.BlockView> blocks(@QueryValue Instant from, @QueryValue Instant to, Authentication auth) {
        return appointments.blocks(SaludAccess.of(auth), from, to);
    }

    @Post("/blocks")
    @Status(HttpStatus.CREATED)
    public AgendaDtos.BlockView createBlock(@Valid @Body AgendaDtos.BlockRequest request, Authentication auth) {
        return appointments.createBlock(SaludAccess.of(auth), request);
    }

    @Delete("/blocks/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public void deleteBlock(Long id, Authentication auth) {
        appointments.deleteBlock(SaludAccess.of(auth), id);
    }
}
