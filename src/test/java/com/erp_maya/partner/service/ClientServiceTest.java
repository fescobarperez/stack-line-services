package com.erp_maya.partner.service;

import com.erp_maya.partner.domain.Client;
import com.erp_maya.partner.dto.ClientDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Editar un cliente no debe borrar su saldo inicial. */
class ClientServiceTest {

    private static ClientDtos.Request request(BigDecimal openingBalance) {
        return new ClientDtos.Request("Ana", "CF", "CF", null, null, null,
                BigDecimal.ZERO, 0, openingBalance, "active");
    }

    @Test
    void editarSinSaldoInicialConservaElQueTenia() {
        Client c = new Client();
        c.setOpeningBalance(new BigDecimal("150.00"));
        ClientService.apply(c, request(null));
        assertEquals(new BigDecimal("150.00"), c.getOpeningBalance());
    }

    @Test
    void elSaldoInicialCambiaSoloSiSeManda() {
        Client c = new Client();
        c.setOpeningBalance(new BigDecimal("150.00"));
        ClientService.apply(c, request(new BigDecimal("80.00")));
        assertEquals(new BigDecimal("80.00"), c.getOpeningBalance());
    }

    @Test
    void unClienteNuevoArrancaEnCero() {
        Client c = new Client();
        ClientService.apply(c, request(null));
        assertEquals(BigDecimal.ZERO, c.getOpeningBalance());
    }
}
