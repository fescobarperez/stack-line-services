package com.erp_maya.mail.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Configuración del servidor de correo saliente. */
public final class MailSettingsDtos {

    private MailSettingsDtos() {}

    /**
     * Lo que se devuelve. Sin `password`: en su lugar va `hasPassword`, que es
     * lo único que la pantalla necesita saber para decidir si muestra
     * «ya hay credenciales guardadas».
     */
    @Serdeable
    public record Response(String host, Integer port, String username,
                           Boolean hasPassword, String fromEmail, String fromName,
                           String security, Boolean enabled) {}

    /** Resultado de la prueba de conexión: nunca lanza, siempre informa. */
    @Serdeable
    public record TestResult(Boolean ok, String message) {}

    /**
     * Lo que se envía. `password` nulo o vacío CONSERVA la guardada: si se
     * borrara al no mandarla, cualquier cambio de puerto dejaría el correo sin
     * credencial. Para quitarla está `clearPassword`.
     */
    @Serdeable
    public record Request(String host,
                          @Min(1) @Max(65535) Integer port,
                          String username,
                          String password,
                          Boolean clearPassword,
                          String fromEmail,
                          String fromName,
                          String security,
                          Boolean enabled) {}
}
