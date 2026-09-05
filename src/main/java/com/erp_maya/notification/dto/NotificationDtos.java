package com.erp_maya.notification.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** Notificación derivada (alerta calculada en tiempo real). */
public final class NotificationDtos {

    private NotificationDtos() {}

    @Serdeable
    public record Notification(String id, String type, String title, String body, String route, Instant createdAt) {}
}
