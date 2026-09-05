package com.erp_maya.payroll.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class EmployeeDtos {

    private EmployeeDtos() {}

    @Serdeable
    public record Request(@NotBlank String employeeCode, @NotBlank String name, String department,
                          String position, BigDecimal salary, String status, LocalDate hiredDate,
                          String dpi, String nit, String bankName, String bankAccount,
                          Long branchId, Long userId) {}

    @Serdeable
    public record Response(Long id, String employeeCode, String name, String department, String position,
                           BigDecimal salary, String status, LocalDate hiredDate, String dpi, String nit,
                           String bankName, String bankAccount, Long branchId, Long userId) {}
}
