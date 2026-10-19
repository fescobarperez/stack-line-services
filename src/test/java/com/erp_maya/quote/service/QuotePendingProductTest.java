package com.erp_maya.quote.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.quote.domain.QuoteItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Una línea con un producto que propuso el asistente y sigue sin revisar
 * queda «por cotizar» ({@link QuoteService#porCotizarSiPendiente}).
 */
class QuotePendingProductTest {

    @Test
    void productoPendienteSinPrecioQuedaPorCotizar() {
        QuoteItem item = linea(Product.REVIEW_PENDING);
        QuoteService.porCotizarSiPendiente(item, BigDecimal.ZERO);
        assertEquals("review", item.getSizePricing());
    }

    @Test
    void siElAsesorYaLePusoPrecioNoSeToca() {
        QuoteItem item = linea(Product.REVIEW_PENDING);
        QuoteService.porCotizarSiPendiente(item, new BigDecimal("1500"));
        assertNull(item.getSizePricing());
    }

    @Test
    void unProductoNormalNoSeMarca() {
        QuoteItem item = linea(null);
        QuoteService.porCotizarSiPendiente(item, BigDecimal.ZERO);
        assertNull(item.getSizePricing());
    }

    @Test
    void unaLineaSinProductoNoSeMarca() {
        QuoteItem item = new QuoteItem();
        QuoteService.porCotizarSiPendiente(item, BigDecimal.ZERO);
        assertNull(item.getSizePricing());
    }

    private static QuoteItem linea(String reviewStatus) {
        Product p = new Product();
        p.setReviewStatus(reviewStatus);
        QuoteItem item = new QuoteItem();
        item.setProduct(p);
        return item;
    }
}
