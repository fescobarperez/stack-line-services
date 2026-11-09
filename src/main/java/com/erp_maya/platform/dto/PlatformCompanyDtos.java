package com.erp_maya.platform.dto;

import com.erp_maya.platform.service.InvitationService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Empresas en la consola (API.md · «Empresas»). */
public final class PlatformCompanyDtos {

    private PlatformCompanyDtos() {}

    /** Plan de la empresa. Hasta el paso 3 (planes) solo trae el código guardado en companies.plan. */
    @Serdeable
    public record PlanRef(@Nullable Long id, String code, String name) {}

    @Serdeable
    public record Summary(Long id, String code, String name, String nit, String status, @Nullable PlanRef plan,
                          String baseCurrency, int usersCount, int branchesCount,
                          Instant createdAt, @Nullable Instant lastActivityAt) {}

    @Serdeable
    public record ListResponse(List<Summary> items, int total, Map<String, Integer> counts) {}

    @Serdeable
    public record EstablishmentView(Long id, Integer satCode, String commercialName, @Nullable String address, @Nullable String phone) {}

    @Serdeable
    public record BranchView(Long id, String name, @Nullable String address, @Nullable Long establishmentId, String status) {}

    @Serdeable
    public record AdminView(Long id, String name, String email, String status, @Nullable Instant lastSeenAt) {}

    @Serdeable
    public record StatusEntry(String status, @Nullable String reason, String by, Instant at) {}

    @Serdeable
    public record Detail(Long id, String code, String name, String nit, String status, @Nullable PlanRef plan,
                         String baseCurrency, int usersCount, Instant createdAt, @Nullable Instant lastActivityAt,
                         List<EstablishmentView> establishments, List<BranchView> branches,
                         List<AdminView> admins, List<StatusEntry> statusHistory,
                         @Nullable InvitationService.Invitation invitation) {}

    @Serdeable
    public record CheckCode(boolean available) {}

    // ── Entrada ──

    @Serdeable
    public record CompanyIn(@Nullable String code, @Nullable String name, @Nullable String nit,
                            @Nullable String baseCurrency, @Nullable String status, @Nullable Long planId) {}

    @Serdeable
    public record EstablishmentIn(@Nullable Integer satCode, @Nullable String commercialName,
                                  @Nullable String address, @Nullable String phone) {}

    @Serdeable
    public record BranchIn(@Nullable String name, @Nullable String address) {}

    @Serdeable
    public record AdminIn(@Nullable String name, @Nullable String email) {}

    @Serdeable
    public record CreateRequest(@Nullable CompanyIn company, @Nullable EstablishmentIn establishment,
                                @Nullable BranchIn branch, @Nullable AdminIn admin) {}

    @Serdeable
    public record UpdateRequest(@Nullable String name, @Nullable String nit, @Nullable String baseCurrency) {}

    @Serdeable
    public record StatusRequest(@Nullable String status, @Nullable String reason) {}
}
