package com.erp_maya.mail.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.mail.domain.CompanyMailSettings;
import jakarta.inject.Singleton;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Envío de correo con el servidor SMTP de cada empresa.
 *
 * La sesión se arma por envío y no una vez al arrancar: el servidor es del
 * inquilino, cambia entre empresas y el usuario puede reconfigurarlo sin
 * reiniciar nada.
 */
@Singleton
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    /**
     * Un SMTP que no responde no puede dejar colgada la petición que lo
     * disparó: diez segundos es de sobra para un servidor sano y poco para
     * que el usuario piense que la aplicación se trabó.
     */
    private static final String TIMEOUT_MS = "10000";

    private final MailSettingsService settings;
    private final TenantContext tenant;

    public MailService(MailSettingsService settings, TenantContext tenant) {
        this.settings = settings;
        this.tenant = tenant;
    }

    /** Lo que el sistema puede o no hacer con el correo, sin lanzar. */
    public record Resultado(boolean ok, String mensaje) {}

    /**
     * Autentica contra el servidor y cierra. No manda ningún mensaje: sirve
     * para saber si los datos capturados funcionan ANTES de que una factura
     * real dependa de ellos.
     *
     * Funciona aunque el correo esté desactivado: lo normal es probar primero
     * y activar después.
     */
    public Resultado probarConexion() {
        CompanyMailSettings c = settings.raw(tenant.getCompanyId());
        if (c == null || esVacio(c.getHost())) {
            return new Resultado(false, "Falta el host SMTP. Guarda la configuración antes de probar.");
        }
        try {
            Transport t = sesion(c).getTransport("smtp");
            if (!esVacio(c.getUsername())) {
                t.connect(c.getHost(), c.getPort(), c.getUsername(), c.getPassword());
            } else {
                t.connect();
            }
            t.close();
            return new Resultado(true, "Conexión correcta con " + c.getHost() + ":" + c.getPort() + ".");
        } catch (AuthenticationFailedException e) {
            // El fallo más común con Gmail: la contraseña de la cuenta en vez
            // de una contraseña de aplicación.
            return new Resultado(false, "El servidor rechazó las credenciales. "
                    + "Si usas Gmail, necesitas una contraseña de aplicación, no la de tu cuenta.");
        } catch (MessagingException e) {
            return new Resultado(false, "No se pudo conectar con " + c.getHost() + ":" + c.getPort()
                    + ". Revisa el host, el puerto y el tipo de cifrado. (" + raiz(e) + ")");
        }
    }

    /**
     * Envía un correo con el servidor de la empresa.
     *
     * Devuelve el resultado en vez de lanzar: quien lo llama está emitiendo una
     * factura o enviando una cotización, y que el correo falle no puede tumbar
     * esa operación. El fallo se informa, no se propaga.
     */
    public Resultado enviar(String para, String asunto, String cuerpoHtml) {
        return enviar(para, asunto, cuerpoHtml, null, null);
    }

    /**
     * Igual, con un archivo adjunto. `adjunto` nulo manda solo el cuerpo.
     */
    public Resultado enviar(String para, String asunto, String cuerpoHtml,
                            byte[] adjunto, String nombreAdjunto) {
        Long companyId = tenant.getCompanyId();
        CompanyMailSettings c = settings.raw(companyId);
        if (c == null || !Boolean.TRUE.equals(c.getEnabled())) {
            return new Resultado(false, "El correo saliente está desactivado en la configuración.");
        }
        if (esVacio(c.getHost()) || esVacio(c.getFromEmail())) {
            return new Resultado(false, "Falta el host o el remitente en la configuración de correo.");
        }
        if (esVacio(para)) {
            return new Resultado(false, "El destinatario no tiene correo registrado.");
        }
        try {
            MimeMessage m = new MimeMessage(sesion(c));
            m.setFrom(remitente(c));
            m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(para, false));
            m.setSubject(asunto, "UTF-8");

            if (adjunto == null || adjunto.length == 0) {
                m.setContent(cuerpoHtml, "text/html; charset=UTF-8");
            } else {
                MimeBodyPart texto = new MimeBodyPart();
                texto.setContent(cuerpoHtml, "text/html; charset=UTF-8");
                MimeBodyPart archivo = new MimeBodyPart();
                archivo.setDataHandler(new jakarta.activation.DataHandler(
                        new ByteArrayDataSource(adjunto, "application/pdf")));
                archivo.setFileName(nombreAdjunto == null ? "documento.pdf" : nombreAdjunto);
                MimeMultipart cuerpo = new MimeMultipart();
                cuerpo.addBodyPart(texto);
                cuerpo.addBodyPart(archivo);
                m.setContent(cuerpo);
            }
            enviarPor(c, m);
            return new Resultado(true, "Correo enviado a " + para + ".");
        } catch (AuthenticationFailedException e) {
            log.warn("SMTP rechazó credenciales de la empresa {}", companyId);
            return new Resultado(false, "El servidor de correo rechazó las credenciales.");
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.warn("Fallo al enviar correo de la empresa {}: {}", companyId, e.toString());
            return new Resultado(false, "No se pudo enviar el correo: " + raiz(e));
        }
    }

    /** ¿Puede el sistema enviar ahora mismo? Para decidir sin intentarlo. */
    public boolean activo() {
        CompanyMailSettings c = settings.raw(tenant.getCompanyId());
        return c != null && Boolean.TRUE.equals(c.getEnabled())
                && !esVacio(c.getHost()) && !esVacio(c.getFromEmail());
    }

    private void enviarPor(CompanyMailSettings c, MimeMessage m) throws MessagingException {
        try (Transport t = sesion(c).getTransport("smtp")) {
            if (!esVacio(c.getUsername())) {
                t.connect(c.getHost(), c.getPort(), c.getUsername(), c.getPassword());
            } else {
                t.connect();
            }
            t.sendMessage(m, m.getAllRecipients());
        }
    }

    private InternetAddress remitente(CompanyMailSettings c)
            throws UnsupportedEncodingException, MessagingException {
        return esVacio(c.getFromName())
                ? new InternetAddress(c.getFromEmail())
                : new InternetAddress(c.getFromEmail(), c.getFromName(), "UTF-8");
    }

    /**
     * Sesión SMTP según el cifrado configurado.
     *
     * `starttls` abre en claro y sube a TLS (587); `ssl` cifra desde el saludo
     * (465). Confundirlos es el segundo error más común después de la
     * contraseña, y por eso el cifrado es un campo propio y no se deduce del
     * puerto.
     */
    private Session sesion(CompanyMailSettings c) {
        Properties p = new Properties();
        p.put("mail.smtp.host", c.getHost());
        p.put("mail.smtp.port", String.valueOf(c.getPort()));
        p.put("mail.smtp.auth", String.valueOf(!esVacio(c.getUsername())));
        p.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        p.put("mail.smtp.timeout", TIMEOUT_MS);
        p.put("mail.smtp.writetimeout", TIMEOUT_MS);

        if ("ssl".equalsIgnoreCase(c.getSecurity())) {
            p.put("mail.smtp.ssl.enable", "true");
        } else if ("starttls".equalsIgnoreCase(c.getSecurity())) {
            p.put("mail.smtp.starttls.enable", "true");
            // `required` para que no caiga en claro sin avisar si el servidor
            // no ofrece STARTTLS: mandar la credencial sin cifrar es peor que
            // fallar.
            p.put("mail.smtp.starttls.required", "true");
        }

        if (esVacio(c.getUsername())) return Session.getInstance(p);
        return Session.getInstance(p, new jakarta.mail.Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(c.getUsername(), c.getPassword());
            }
        });
    }

    private static boolean esVacio(String v) { return v == null || v.isBlank(); }

    /** El mensaje de la causa raíz, que suele decir más que el de arriba. */
    private static String raiz(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
