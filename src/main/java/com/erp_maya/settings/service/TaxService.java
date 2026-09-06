package com.erp_maya.settings.service;

import com.erp_maya.common.TenantContext;
import com.erp_maya.settings.repository.CompanySettingRepository;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tasa de IVA de la empresa.
 *
 * Antes era una constante `IVA_FACTOR = 12/112` repetida en tres servicios, y
 * el campo "Tasa IVA" de Configuración no lo leía nadie: se podía poner 5% y
 * seguía facturando al 12%.
 *
 * La tasa se guarda en `company_settings` con la clave `tax.iva_rate` como
 * PORCENTAJE ("12", "5"). Quien la necesite pide `rate()` para el porcentaje o
 * `factorOverGross()` para desglosar un precio que ya lo incluye.
 */
@Singleton
public class TaxService {

    public static final String KEY = "tax.iva_rate";
    /** Guatemala. Solo aplica si la empresa no ha configurado la suya. */
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("12");

    private final CompanySettingRepository settings;
    private final TenantContext tenant;

    public TaxService(CompanySettingRepository settings, TenantContext tenant) {
        this.settings = settings;
        this.tenant = tenant;
    }

    /** Porcentaje configurado: 12 significa 12%. */
    public BigDecimal rate() {
        return settings.findByCompanyIdAndSettingKey(tenant.getCompanyId(), KEY)
                .map(s -> {
                    try {
                        BigDecimal v = new BigDecimal(s.getSettingValue().trim());
                        // Tolera "0.12" además de "12": abajo de 1 se interpreta como fracción.
                        if (v.compareTo(BigDecimal.ONE) < 0 && v.signum() > 0) {
                            v = v.multiply(new BigDecimal("100"));
                        }
                        return (v.signum() < 0 || v.compareTo(new BigDecimal("100")) > 0) ? DEFAULT_RATE : v;
                    } catch (NumberFormatException e) {
                        return DEFAULT_RATE;   // valor corrupto no debe romper una venta
                    }
                })
                .orElse(DEFAULT_RATE);
    }

    /**
     * Factor para extraer el impuesto de un importe que YA lo incluye:
     * con 12% devuelve 12/112. Es como calculan POS y cotizaciones.
     */
    public BigDecimal factorOverGross() {
        return factorOverGross(rate());
    }

    /** Factor para pasar de importe con impuesto a base: con 12% devuelve 1.12. */
    public BigDecimal grossFactor() {
        return grossFactor(rate());
    }

    public static BigDecimal grossFactor(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) return BigDecimal.ONE;
        return BigDecimal.ONE.add(rate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
    }

    public static BigDecimal factorOverGross(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) return BigDecimal.ZERO;
        return rate.divide(rate.add(new BigDecimal("100")), 10, RoundingMode.HALF_UP);
    }
}
