package com.erp_maya.accounting.controller;

import com.erp_maya.accounting.dto.JournalEntryDtos;
import com.erp_maya.accounting.service.JournalEntryService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/journal-entries")
public class JournalEntryController {

    private final JournalEntryService service;

    public JournalEntryController(JournalEntryService service) {
        this.service = service;
    }

    @Get
    public Page<JournalEntryDtos.Response> list(Pageable pageable) {
        return service.list(pageable);
    }

    @Get("/{id}")
    public JournalEntryDtos.Response get(Long id) {
        return service.get(id);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public JournalEntryDtos.Response create(@Valid @Body JournalEntryDtos.Request request) {
        return service.create(request);
    }
}
