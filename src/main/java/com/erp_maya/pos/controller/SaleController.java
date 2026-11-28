package com.erp_maya.pos.controller;

import com.erp_maya.mail.dto.MailSettingsDtos;
import com.erp_maya.mail.service.MailService;
import com.erp_maya.pos.dto.SaleDtos;
import com.erp_maya.pos.service.SaleService;
import com.erp_maya.pos.service.SaleTicketService;
import com.erp_maya.pos.service.SaleVoidService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;

@Controller("/api/sales")
public class SaleController {

    private final SaleService service;
    private final SaleTicketService tickets;
    private final SaleVoidService voids;

    public SaleController(SaleService service, SaleTicketService tickets, SaleVoidService voids) {
        this.service = service;
        this.tickets = tickets;
        this.voids = voids;
    }

    /**
     * Anula la venta: su DTE, su partida, el inventario, la caja abierta y los
     * usos de promoción. Queda quién la anuló y por qué.
     */
    @Post("/{id}/void")
    public SaleDtos.Response voidSale(Long id, @Valid @Body SaleDtos.VoidRequest request,
                                      @Nullable Authentication authentication) {
        Object uid = authentication == null ? null : authentication.getAttributes().get("userId");
        return voids.anular(id, request.reason(), uid instanceof Number n ? n.longValue() : null);
    }

    @Get
    public Page<SaleDtos.Response> list(Pageable pageable) {
        return service.list(pageable);
    }

    /** Registra que el impreso original salió hacia el cliente. */
    @Post("/{id}/deliver")
    public SaleDtos.Response deliver(Long id, @Nullable @Body SaleDtos.DeliverRequest request) {
        return service.deliver(id, request);
    }

    @Get("/{id}")
    public SaleDtos.Response get(Long id) {
        return service.get(id);
    }

    /** El ticket con los datos reales de la venta (lo que pinta e imprime el POS). */
    @Get("/{id}/ticket")
    public SaleDtos.Ticket ticket(Long id) {
        return tickets.ticket(id);
    }

    /** El ticket en PDF, para descargarlo. */
    @Get("/{id}/ticket.pdf")
    @Produces("application/pdf")
    public HttpResponse<byte[]> ticketPdf(Long id) {
        SaleDtos.Ticket t = tickets.ticket(id);
        return HttpResponse.ok(tickets.pdf(t))
                .header("Content-Disposition", "attachment; filename=\"" + SaleTicketService.archivo(t) + "\"");
    }

    /** Manda el ticket en PDF al correo indicado, con el SMTP de la empresa. */
    @Post("/{id}/send-email")
    public MailSettingsDtos.TestResult sendTicket(Long id, @Valid @Body SaleDtos.SendTicketRequest request) {
        MailService.Resultado r = tickets.enviar(tickets.ticket(id), request.to());
        return new MailSettingsDtos.TestResult(r.ok(), r.mensaje());
    }

    @Post
    @Status(HttpStatus.CREATED)
    public SaleDtos.Response create(@Valid @Body SaleDtos.Request request) {
        return service.create(request);
    }
}
