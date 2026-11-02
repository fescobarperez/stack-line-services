package com.erp_maya.salud.service;

import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.pos.domain.CashRegister;
import com.erp_maya.pos.dto.SaleDtos;
import com.erp_maya.pos.repository.CashRegisterRepository;
import com.erp_maya.pos.repository.SaleRepository;
import com.erp_maya.pos.service.SaleService;
import com.erp_maya.salud.domain.Appointment;
import com.erp_maya.salud.domain.AppointmentType;
import com.erp_maya.salud.domain.Charge;
import com.erp_maya.salud.domain.PatientPackage;
import com.erp_maya.salud.domain.PatientRow;
import com.erp_maya.salud.domain.SaludCredit;
import com.erp_maya.salud.domain.SaludPackage;
import com.erp_maya.salud.dto.CajaDtos;
import com.erp_maya.salud.repository.AppointmentRepository;
import com.erp_maya.salud.repository.AppointmentTypeRepository;
import com.erp_maya.salud.repository.ChargeRepository;
import com.erp_maya.salud.repository.PatientPackageRepository;
import com.erp_maya.salud.repository.PatientRowRepository;
import com.erp_maya.salud.repository.SaludCreditRepository;
import com.erp_maya.salud.repository.SaludPackageRepository;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.erp_maya.salud.service.SaludAccess.AGENDA_CONFIG;
import static com.erp_maya.salud.service.SaludAccess.BILLING;
import static com.erp_maya.salud.service.SaludAccess.CREAR;
import static com.erp_maya.salud.service.SaludAccess.EDITAR;
import static com.erp_maya.salud.service.SaludAccess.VER;

/**
 * Caja de Stackline Salud: cobro de citas, paquetes de sesiones y anticipos.
 *
 * No duplica la facturación: todo lo que mueve dinero se emite con
 * {@link SaleService} (mismo correlativo, FEL, partida contable y cuadre del
 * turno de caja) a nombre del cliente que es el paciente, en el turno de caja
 * abierto de quien cobra. Lo propio de Salud vive aquí:
 *
 *   - un paquete se factura al venderlo y cada cita de su tipo descuenta una
 *     sesión, sin volver a facturar;
 *   - un anticipo se factura al recibirlo (SAT lo pide así) y queda como
 *     saldo; aplicarlo a una cita no genera otra factura, solo la del resto;
 *   - la cortesía no cobra, pero deja constancia y motivo.
 *
 * Permiso `salud_billing`: ver, crear (cobrar, vender, recibir) y editar
 * (anular, cortesía). El catálogo de paquetes es de `agenda_config`.
 */
@Singleton
public class SaludBillingService {

    private final AppointmentRepository appointments;
    private final AppointmentTypeRepository types;
    private final PatientRowRepository patients;
    private final ChargeRepository charges;
    private final PatientPackageRepository patientPackages;
    private final SaludPackageRepository packages;
    private final SaludCreditRepository credits;
    private final CashRegisterRepository registers;
    private final SaleRepository sales;
    private final SaleService saleService;
    private final TenantContext tenant;

    public SaludBillingService(AppointmentRepository appointments, AppointmentTypeRepository types,
                               PatientRowRepository patients, ChargeRepository charges,
                               PatientPackageRepository patientPackages, SaludPackageRepository packages,
                               SaludCreditRepository credits, CashRegisterRepository registers, SaleRepository sales,
                               SaleService saleService, TenantContext tenant) {
        this.appointments = appointments;
        this.types = types;
        this.patients = patients;
        this.charges = charges;
        this.patientPackages = patientPackages;
        this.packages = packages;
        this.credits = credits;
        this.registers = registers;
        this.sales = sales;
        this.saleService = saleService;
        this.tenant = tenant;
    }

    // ── Catálogo de paquetes ─────────────────────────────────────────────

    /** Lo ven configuración (para editarlo) y caja (para venderlo). */
    @Transactional
    public List<CajaDtos.PackageView> listPackages(SaludAccess.Actor actor) {
        if (!actor.can(AGENDA_CONFIG, VER)) actor.require(BILLING, VER);
        Map<Long, String> tipos = nombresDeTipos();
        return packages.findByCompanyIdOrderByName(tenant.getCompanyId()).stream()
                .map(p -> toView(p, tipos.get(p.getTypeId()))).toList();
    }

    @Transactional
    public CajaDtos.PackageView createPackage(SaludAccess.Actor actor, CajaDtos.PackageRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        SaludPackage p = new SaludPackage();
        p.setCompanyId(tenant.getCompanyId());
        aplicar(p, req, null);
        p = packages.save(p);
        return toView(p, nombresDeTipos().get(p.getTypeId()));
    }

    @Transactional
    public CajaDtos.PackageView updatePackage(SaludAccess.Actor actor, Long id, CajaDtos.PackageRequest req) {
        actor.require(AGENDA_CONFIG, EDITAR);
        SaludPackage p = packages.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Paquete " + id + " no encontrado"));
        aplicar(p, req, id);
        p = packages.update(p);
        return toView(p, nombresDeTipos().get(p.getTypeId()));
    }

    private void aplicar(SaludPackage p, CajaDtos.PackageRequest req, @Nullable Long id) {
        String nombre = req.name().trim();
        boolean repetido = packages.findByCompanyIdOrderByName(tenant.getCompanyId()).stream()
                .anyMatch(o -> !o.getId().equals(id) && o.getName().equalsIgnoreCase(nombre));
        if (repetido) throw new IllegalStateException("Ya hay un paquete llamado «" + nombre + "».");
        tipo(req.typeId());
        p.setName(nombre);
        p.setTypeId(req.typeId());
        p.setSessions(CajaRules.sesionesPaquete(req.sessions()));
        p.setPrice(CajaRules.monto(req.price(), "el precio del paquete", true));
        p.setValidDays(CajaRules.vigencia(req.validDays()));
        if (req.active() != null) p.setActive(req.active());
    }

    private static CajaDtos.PackageView toView(SaludPackage p, @Nullable String typeName) {
        return new CajaDtos.PackageView(p.getId(), p.getName(), p.getTypeId(), typeName, p.getSessions(), p.getPrice(),
                CajaRules.valorSesion(p.getPrice(), p.getSessions()), p.getValidDays(), p.isActive());
    }

    // ── Cuenta del paciente ──────────────────────────────────────────────

    @Transactional
    public CajaDtos.Account account(SaludAccess.Actor actor, Long patientId) {
        actor.require(BILLING, VER);
        Long companyId = tenant.getCompanyId();
        paciente(patientId);
        LocalDate hoy = PatientRules.today();
        Map<Long, String> tipos = nombresDeTipos();
        List<PatientPackage> suyos = patientPackages.findByPatientIdAndCompanyIdOrderByPurchasedAtDesc(patientId, companyId);
        Map<Long, String> nombresPaquete = new HashMap<>();
        suyos.forEach(p -> nombresPaquete.put(p.getId(), p.getName()));
        List<Charge> cobros = charges.findByPatientIdAndCompanyIdOrderByCreatedAtDesc(patientId, companyId);
        Map<Long, String> facturas = numeros(cobros.stream().map(Charge::getSaleId).toList());
        Map<Long, Instant> inicios = new HashMap<>();
        for (Charge c : cobros.stream().limit(LIMITE).toList()) {
            appointments.findById(c.getAppointmentId()).ifPresent(a -> inicios.put(a.getId(), a.getStartsAt()));
        }
        return new CajaDtos.Account(patientId, saldo(patientId),
                suyos.stream().map(p -> CajaRules.toView(p, tipos.get(p.getTypeId()), hoy)).toList(),
                cobros.stream().limit(LIMITE).map(c -> toView(c, tipos.get(c.getTypeId()), inicios.get(c.getAppointmentId()),
                        facturas.get(c.getSaleId()), nombresPaquete.get(c.getPatientPackageId()))).toList(),
                credits.findByPatientIdAndCompanyIdOrderByCreatedAtDesc(patientId, companyId).stream().limit(LIMITE)
                        .map(SaludBillingService::toView).toList());
    }

    /** Vende un paquete: factura su precio y deja las sesiones disponibles. */
    @Transactional
    public CajaDtos.PatientPackageView sellPackage(SaludAccess.Actor actor, Long patientId, CajaDtos.SellPackageRequest req) {
        actor.require(BILLING, CREAR);
        Long companyId = tenant.getCompanyId();
        PatientRow paciente = activo(paciente(patientId));
        SaludPackage pk = packages.findByIdAndCompanyId(req.packageId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Paquete " + req.packageId() + " no encontrado"));
        if (!pk.isActive()) throw new IllegalStateException("El paquete «" + pk.getName() + "» está desactivado.");
        AppointmentType tipo = tipo(pk.getTypeId());
        Long venta = null;
        if (pk.getPrice().signum() > 0) {
            venta = facturar(actor, paciente.getId(), "Paquete " + pk.getName() + " (" + pk.getSessions() + " sesiones de "
                    + tipo.getName() + ")", pk.getPrice(), CajaRules.pago(req.paymentMethod())).id();
        }
        Instant ahora = Instant.now();
        PatientPackage p = new PatientPackage();
        p.setCompanyId(companyId);
        p.setPatientId(paciente.getId());
        p.setPackageId(pk.getId());
        p.setName(pk.getName());
        p.setTypeId(pk.getTypeId());
        p.setSessionsTotal(pk.getSessions());
        p.setSessionsUsed(0);
        p.setPrice(pk.getPrice());
        p.setSaleId(venta);
        p.setPurchasedAt(ahora);
        p.setExpiresOn(pk.getValidDays() == null ? null : PatientRules.today().plusDays(pk.getValidDays()));
        p.setStatus(CajaRules.PK_ACTIVO);
        p.setCreatedBy(actor.userId());
        return CajaRules.toView(patientPackages.save(p), tipo.getName(), PatientRules.today());
    }

    /** Recibe un anticipo: se factura ahora y queda como saldo del paciente. */
    @Transactional
    public CajaDtos.CreditView deposit(SaludAccess.Actor actor, Long patientId, CajaDtos.DepositRequest req) {
        actor.require(BILLING, CREAR);
        PatientRow paciente = activo(paciente(patientId));
        BigDecimal monto = CajaRules.monto(req.amount(), "el monto del anticipo", false);
        SaleDtos.Response venta = facturar(actor, paciente.getId(), "Anticipo por servicios de salud", monto,
                CajaRules.pago(req.paymentMethod()));
        return toView(movimiento(paciente.getId(), CajaRules.CR_ANTICIPO, monto, venta.id(), null, blank(req.notes()), actor));
    }

    // ── Cobro de la cita ─────────────────────────────────────────────────

    /** Lo que el diálogo de cobro necesita: precio, paquetes que sirven, saldo, caja abierta y el cobro si ya hay. */
    @Transactional
    public CajaDtos.ChargeOptions options(SaludAccess.Actor actor, Long appointmentId) {
        actor.require(BILLING, VER);
        Appointment a = cita(appointmentId);
        AppointmentType tipo = tipo(a.getTypeId());
        PatientRow paciente = paciente(a.getPatientId());
        LocalDate hoy = PatientRules.today();
        List<CajaDtos.PatientPackageView> sirven = CajaRules.aplicables(
                        patientPackages.findByPatientIdAndCompanyIdAndStatusOrderByPurchasedAt(
                                a.getPatientId(), a.getCompanyId(), CajaRules.PK_ACTIVO), a.getTypeId(), hoy)
                .stream().map(p -> CajaRules.toView(p, tipo.getName(), hoy)).toList();
        CajaDtos.ChargeView actual = charges.findByAppointmentIdAndStatus(a.getId(), CajaRules.VIGENTE)
                .map(c -> view(c, tipo.getName(), a.getStartsAt())).orElse(null);
        return new CajaDtos.ChargeOptions(a.getId(), a.getStatus(), paciente.getId(), paciente.getName(), tipo.getId(),
                tipo.getName(), tipo.getPrice(), saldo(a.getPatientId()), sirven, turno(actor) != null, actual);
    }

    @Transactional
    public CajaDtos.ChargeView charge(SaludAccess.Actor actor, Long appointmentId, CajaDtos.ChargeRequest req) {
        actor.require(BILLING, CREAR);
        Appointment a = cita(appointmentId);
        if (CajaRules.NO_COBRABLES.contains(a.getStatus())) {
            throw new IllegalStateException("La cita está «" + a.getStatus().replace('_', ' ') + "»: no se cobra.");
        }
        if (charges.findByAppointmentIdAndStatus(a.getId(), CajaRules.VIGENTE).isPresent()) {
            throw new IllegalStateException("Esta cita ya está cobrada. Para cobrarla de otra forma, anula el cobro primero.");
        }
        AppointmentType tipo = tipo(a.getTypeId());
        Charge c = nuevo(a, actor);
        c.setNotes(blank(req.notes()));

        if (Boolean.TRUE.equals(req.courtesy())) {
            actor.require(BILLING, EDITAR);
            if (c.getNotes() == null) throw new IllegalStateException("Indica el motivo de la cortesía.");
            BigDecimal precio = req.price() != null ? CajaRules.monto(req.price(), "el precio", true) : CajaRules.cero(tipo.getPrice());
            c.setMethod(CajaRules.CORTESIA);
            c.setPrice(CajaRules.q(precio));
            c.setDiscount(CajaRules.q(precio));
            c.setTotal(BigDecimal.ZERO);
            c.setServiceValue(BigDecimal.ZERO);
            return guardar(c, tipo, a);
        }

        if (req.patientPackageId() != null) {
            PatientPackage p = patientPackages.findByIdAndCompanyId(req.patientPackageId(), a.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Paquete " + req.patientPackageId() + " no encontrado"));
            if (!p.getPatientId().equals(a.getPatientId())) throw new IllegalStateException("Ese paquete es de otro paciente.");
            return conPaquete(c, p, tipo, a);
        }

        BigDecimal precio = req.price() != null ? req.price() : tipo.getPrice();
        if (precio == null) {
            throw new IllegalStateException("El tipo «" + tipo.getName() + "» no tiene precio: indica cuánto se cobra.");
        }
        CajaRules.Reparto r = CajaRules.repartir(precio, req.discount(), saldo(a.getPatientId()),
                Boolean.TRUE.equals(req.useCredit()));
        if (r.total().signum() == 0) {
            throw new IllegalStateException("El total es cero: si no se cobra, regístralo como cortesía con su motivo.");
        }
        c.setMethod(r.metodo());
        c.setPrice(CajaRules.q(precio));
        c.setDiscount(CajaRules.q(CajaRules.cero(req.discount())));
        c.setTotal(r.total());
        c.setCreditAmount(r.credito());
        c.setCashAmount(r.caja());
        c.setServiceValue(r.total());
        if (r.caja().signum() > 0) {
            String pago = CajaRules.pago(req.paymentMethod());
            c.setPaymentMethod(pago);
            String concepto = tipo.getName() + " — " + AgendaRules.fechaLarga(a.getStartsAt())
                    + (r.credito().signum() > 0 ? " (saldo; anticipo aplicado Q" + r.credito().toPlainString() + ")" : "");
            c.setSaleId(facturar(actor, a.getPatientId(), concepto, r.caja(), pago).id());
        }
        CajaDtos.ChargeView v = guardar(c, tipo, a);
        if (r.credito().signum() > 0) {
            movimiento(a.getPatientId(), CajaRules.CR_APLICADO, r.credito().negate(), null, v.id(),
                    "Aplicado a la cita del " + AgendaRules.fechaLarga(a.getStartsAt()), actor);
        }
        return v;
    }

    /**
     * Al completar una cita sin cobro, si el paciente tiene un paquete que
     * sirve, se descuenta la sesión sola: es lo que la clínica haría a mano
     * todas las veces. Si no hay paquete, la cita queda pendiente de cobro.
     */
    @Transactional
    public @Nullable CajaDtos.ChargeView autoPackage(SaludAccess.Actor actor, Appointment a) {
        if (charges.findByAppointmentIdAndStatus(a.getId(), CajaRules.VIGENTE).isPresent()) return null;
        List<PatientPackage> sirven = CajaRules.aplicables(patientPackages.findByPatientIdAndCompanyIdAndStatusOrderByPurchasedAt(
                a.getPatientId(), a.getCompanyId(), CajaRules.PK_ACTIVO), a.getTypeId(), PatientRules.today());
        if (sirven.isEmpty()) return null;
        AppointmentType tipo = tipo(a.getTypeId());
        Charge c = nuevo(a, actor);
        c.setNotes("Descontado al completar la cita");
        return conPaquete(c, sirven.get(0), tipo, a);
    }

    private CajaDtos.ChargeView conPaquete(Charge c, PatientPackage p, AppointmentType tipo, Appointment a) {
        if (!CajaRules.sirve(p, a.getTypeId(), PatientRules.today())) {
            throw new IllegalStateException("El paquete «" + p.getName() + "» no sirve para esta cita"
                    + (Objects.equals(p.getTypeId(), a.getTypeId()) ? " (sin sesiones, vencido o anulado)." : " (es de otro tipo de cita)."));
        }
        CajaRules.consumir(p);
        patientPackages.update(p);
        BigDecimal valor = CajaRules.valorSesion(p.getPrice(), p.getSessionsTotal());
        c.setMethod(CajaRules.PAQUETE);
        c.setPrice(valor);
        c.setDiscount(BigDecimal.ZERO);
        c.setTotal(BigDecimal.ZERO);
        c.setServiceValue(valor);
        c.setPatientPackageId(p.getId());
        return guardar(c, tipo, a);
    }

    /**
     * Anula el cobro: devuelve la sesión del paquete o el anticipo aplicado.
     * La factura, si la hubo, no se toca aquí: se anula con nota de crédito
     * en Facturación, que es donde vive el control fiscal.
     */
    @Transactional
    public CajaDtos.VoidResult voidCharge(SaludAccess.Actor actor, Long chargeId, CajaDtos.VoidRequest req) {
        actor.require(BILLING, EDITAR);
        Charge c = charges.findByIdAndCompanyId(chargeId, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cobro " + chargeId + " no encontrado"));
        if (!CajaRules.VIGENTE.equals(c.getStatus())) throw new IllegalStateException("Ese cobro ya está anulado.");
        String motivo = blank(req.reason());
        if (motivo == null) throw new IllegalStateException("Indica el motivo de la anulación.");
        c.setStatus(CajaRules.ANULADO);
        c.setVoidedAt(Instant.now());
        c.setVoidedBy(actor.userId());
        c.setVoidReason(motivo);
        charges.update(c);
        if (c.getPatientPackageId() != null) {
            patientPackages.findById(c.getPatientPackageId()).ifPresent(p -> {
                CajaRules.devolver(p);
                patientPackages.update(p);
            });
        }
        if (c.getCreditAmount().signum() > 0) {
            movimiento(c.getPatientId(), CajaRules.CR_REVERSO, c.getCreditAmount(), null, c.getId(),
                    "Cobro anulado: " + motivo, actor);
        }
        String aviso = null;
        if (c.getSaleId() != null) {
            String numero = sales.findByIdAndCompanyId(c.getSaleId(), c.getCompanyId()).map(s -> s.getDocNumber()).orElse("");
            aviso = "La factura " + numero + " sigue vigente: anúlala con una nota de crédito en Facturación.";
        }
        Appointment a = appointments.findById(c.getAppointmentId()).orElse(null);
        return new CajaDtos.VoidResult(view(c, nombresDeTipos().get(c.getTypeId()), a == null ? null : a.getStartsAt()), aviso);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────

    private static final int LIMITE = 50;

    private Charge nuevo(Appointment a, SaludAccess.Actor actor) {
        Charge c = new Charge();
        c.setCompanyId(a.getCompanyId());
        c.setAppointmentId(a.getId());
        c.setPatientId(a.getPatientId());
        c.setTypeId(a.getTypeId());
        c.setProfessionalId(a.getProfessionalId());
        c.setStatus(CajaRules.VIGENTE);
        c.setCreatedBy(actor.userId());
        c.setCreatedAt(Instant.now());
        return c;
    }

    private CajaDtos.ChargeView guardar(Charge c, AppointmentType tipo, Appointment a) {
        Charge g = charges.save(c);
        // salud_v_appointments lee el cobro: sin flush la agenda lo vería tarde en esta misma petición.
        charges.flush();
        return view(g, tipo.getName(), a.getStartsAt());
    }

    /** El turno de caja abierto de quien cobra. */
    private @Nullable CashRegister turno(SaludAccess.Actor actor) {
        if (actor.userId() == null) return null;
        return registers.findByCompanyIdAndUserIdAndStatus(tenant.getCompanyId(), actor.userId(), "open").orElse(null);
    }

    /**
     * Factura de contado por SaleService: correlativo, FEL, partida contable y
     * cuadre del turno. Un renglón de servicio (sin producto, no toca bodega).
     */
    private SaleDtos.Response facturar(SaludAccess.Actor actor, Long clientId, String concepto, BigDecimal monto, String pago) {
        CashRegister caja = turno(actor);
        if (caja == null) {
            throw new IllegalStateException("Para cobrar abre tu turno de caja (Operación → Caja y Cortes).");
        }
        String texto = concepto.length() > 200 ? concepto.substring(0, 200) : concepto;
        return saleService.create(new SaleDtos.Request(null, "FACT", null, null, null, clientId, null, actor.userId(),
                caja.getId(), pago, "paid", false, null, null, null,
                List.of(new SaleDtos.ItemRequest(null, texto, BigDecimal.ONE, monto, null, null, null))));
    }

    private SaludCredit movimiento(Long patientId, String kind, BigDecimal monto, @Nullable Long saleId,
                                   @Nullable Long chargeId, @Nullable String notas, SaludAccess.Actor actor) {
        SaludCredit m = new SaludCredit();
        m.setCompanyId(tenant.getCompanyId());
        m.setPatientId(patientId);
        m.setKind(kind);
        m.setAmount(CajaRules.q(monto));
        m.setSaleId(saleId);
        m.setChargeId(chargeId);
        m.setNotes(notas == null ? null : notas.length() > 300 ? notas.substring(0, 300) : notas);
        m.setCreatedBy(actor.userId());
        m.setCreatedAt(Instant.now());
        return credits.save(m);
    }

    private BigDecimal saldo(Long patientId) {
        return CajaRules.q(CajaRules.cero(credits.balance(patientId, tenant.getCompanyId())));
    }

    private Appointment cita(Long id) {
        return appointments.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita " + id + " no encontrada"));
    }

    private AppointmentType tipo(Long id) {
        return types.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de cita " + id + " no encontrado"));
    }

    private PatientRow paciente(Long id) {
        return patients.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente " + id + " no encontrado"));
    }

    private static PatientRow activo(PatientRow p) {
        if (!"activo".equals(p.getStatus())) {
            throw new IllegalStateException("El paciente está «" + p.getStatus() + "».");
        }
        return p;
    }

    private Map<Long, String> nombresDeTipos() {
        Map<Long, String> m = new HashMap<>();
        types.findByCompanyIdOrderByName(tenant.getCompanyId()).forEach(t -> m.put(t.getId(), t.getName()));
        return m;
    }

    /** Número de documento de cada venta. */
    private Map<Long, String> numeros(List<Long> saleIds) {
        Map<Long, String> m = new HashMap<>();
        for (Long id : saleIds) {
            if (id != null && !m.containsKey(id)) {
                sales.findByIdAndCompanyId(id, tenant.getCompanyId()).ifPresent(s -> m.put(id, s.getDocNumber()));
            }
        }
        return m;
    }

    private CajaDtos.ChargeView view(Charge c, @Nullable String typeName, @Nullable Instant inicio) {
        String numero = c.getSaleId() == null ? null : numeros(List.of(c.getSaleId())).get(c.getSaleId());
        String paquete = c.getPatientPackageId() == null ? null
                : patientPackages.findById(c.getPatientPackageId()).map(PatientPackage::getName).orElse(null);
        return toView(c, typeName, inicio, numero, paquete);
    }

    static CajaDtos.ChargeView toView(Charge c, @Nullable String typeName, @Nullable Instant inicio,
                                      @Nullable String saleNumber, @Nullable String packageName) {
        return new CajaDtos.ChargeView(c.getId(), c.getAppointmentId(), c.getPatientId(), c.getTypeId(), typeName, inicio,
                c.getMethod(), c.getPrice(), c.getDiscount(), c.getTotal(), c.getCreditAmount(), c.getCashAmount(),
                c.getServiceValue(), c.getPaymentMethod(), c.getSaleId(), saleNumber, c.getPatientPackageId(), packageName,
                c.getStatus(), c.getNotes(), c.getCreatedAt(), c.getVoidedAt(), c.getVoidReason());
    }

    static CajaDtos.CreditView toView(SaludCredit m) {
        return new CajaDtos.CreditView(m.getId(), m.getKind(), m.getAmount(), m.getSaleId(), m.getChargeId(),
                m.getNotes(), m.getCreatedAt());
    }

    private static @Nullable String blank(@Nullable String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
