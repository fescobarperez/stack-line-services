package com.erp_maya.salud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Ajustes de Salud de una empresa. Sin fila: recordatorios activos, 24 h antes. Ver 091. */
@Entity
@Table(name = "salud_settings")
public class SaludSettings {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = true;

    @Column(name = "reminder_hours", nullable = false)
    private int reminderHours = 24;

    /** Recordatorio del control por WhatsApp, y cuántos días antes de la fecha límite (fase 7). */
    @Column(name = "control_reminders_enabled", nullable = false)
    private boolean controlRemindersEnabled = true;

    @Column(name = "control_reminder_days", nullable = false)
    private int controlReminderDays = 2;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public boolean isRemindersEnabled() { return remindersEnabled; }
    public void setRemindersEnabled(boolean remindersEnabled) { this.remindersEnabled = remindersEnabled; }

    public int getReminderHours() { return reminderHours; }
    public void setReminderHours(int reminderHours) { this.reminderHours = reminderHours; }

    public boolean isControlRemindersEnabled() { return controlRemindersEnabled; }
    public void setControlRemindersEnabled(boolean v) { this.controlRemindersEnabled = v; }

    public int getControlReminderDays() { return controlReminderDays; }
    public void setControlReminderDays(int v) { this.controlReminderDays = v; }

    public Instant getUpdatedAt() { return updatedAt; }
}
