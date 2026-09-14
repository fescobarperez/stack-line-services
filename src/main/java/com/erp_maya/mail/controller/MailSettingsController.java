package com.erp_maya.mail.controller;

import com.erp_maya.mail.dto.MailSettingsDtos;
import com.erp_maya.mail.service.MailService;
import com.erp_maya.mail.service.MailSettingsService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

/** Servidor de correo saliente de la empresa. La contraseña nunca se devuelve. */
@Controller("/api/company/mail-settings")
public class MailSettingsController {

    private final MailSettingsService service;
    private final MailService mail;

    public MailSettingsController(MailSettingsService service, MailService mail) {
        this.service = service;
        this.mail = mail;
    }

    @Get
    public MailSettingsDtos.Response get() {
        return service.get();
    }

    @Put
    public MailSettingsDtos.Response save(@Valid @Body MailSettingsDtos.Request request) {
        return service.save(request);
    }

    /**
     * Autentica contra el servidor guardado y cierra, sin mandar ningún
     * mensaje. Responde 200 aunque falle: el fallo es el resultado de la
     * prueba, no un error de la petición.
     */
    @Post("/test")
    public MailSettingsDtos.TestResult test() {
        MailService.Resultado r = mail.probarConexion();
        return new MailSettingsDtos.TestResult(r.ok(), r.mensaje());
    }
}
