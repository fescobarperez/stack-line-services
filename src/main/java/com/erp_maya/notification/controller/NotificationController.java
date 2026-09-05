package com.erp_maya.notification.controller;

import com.erp_maya.notification.dto.NotificationDtos.Notification;
import com.erp_maya.notification.service.NotificationService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

/** Notificaciones derivadas (alertas en tiempo real). */
@Controller("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @Get
    public List<Notification> list() {
        return service.list();
    }
}
