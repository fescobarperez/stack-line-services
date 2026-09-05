package com.erp_maya.settings.controller;

import com.erp_maya.settings.dto.CompanySettingDtos;
import com.erp_maya.settings.service.CompanySettingService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/api/settings")
public class CompanySettingController {

    private final CompanySettingService service;

    public CompanySettingController(CompanySettingService service) {
        this.service = service;
    }

    @Get
    public List<CompanySettingDtos.Response> list() {
        return service.list();
    }

    @Get("/{key}")
    public CompanySettingDtos.Response get(String key) {
        return service.get(key);
    }

    @Put("/{key}")
    public CompanySettingDtos.Response put(String key, @Valid @Body CompanySettingDtos.Request request) {
        return service.put(key, request);
    }

    @Delete("/{key}")
    @Status(HttpStatus.NO_CONTENT)
    public void delete(String key) {
        service.delete(key);
    }
}
