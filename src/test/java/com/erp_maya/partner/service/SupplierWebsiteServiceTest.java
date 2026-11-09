package com.erp_maya.partner.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupplierWebsiteServiceTest {

    @Test
    void normalizesHttpUrlsForDuplicateDetection() {
        assertEquals("https://www.example.com/catalog",
                SupplierWebsiteService.normalizeBaseUrl(" HTTPS://WWW.EXAMPLE.COM:443/catalog/ "));
        assertEquals("http://example.com",
                SupplierWebsiteService.normalizeBaseUrl("http://example.com:80/"));
    }

    @Test
    void rejectsUnsupportedOrCredentialedUrls() {
        assertThrows(IllegalStateException.class,
                () -> SupplierWebsiteService.normalizeBaseUrl("ftp://example.com/catalog"));
        assertThrows(IllegalStateException.class,
                () -> SupplierWebsiteService.normalizeBaseUrl("https://user:password@example.com/catalog"));
        assertThrows(IllegalStateException.class,
                () -> SupplierWebsiteService.normalizeBaseUrl("example.com/catalog"));
    }
}
