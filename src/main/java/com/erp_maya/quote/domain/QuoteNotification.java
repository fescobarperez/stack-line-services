package com.erp_maya.quote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Aviso pendiente para el cliente (bandeja de salida hacia agents-services).
 * Se guarda en la misma transacción que el cambio; {@code QuoteNotificationDispatcher}
 * lo entrega con reintentos.
 */
@Entity
@Table(name = "quote_notifications")
public class QuoteNotification {

    public static final String PENDIENTE = "pendiente";
    public static final String ENVIADA = "enviada";
    public static final String FALLIDA = "fallida";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Column(nullable = false)
    private String kind;

    private String channel;

    @Column(name = "conversation_ref")
    private String conversationRef;

    /** JSON con lo que el mensaje dice: armado por el ERP, no por el modelo. */
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private String status = PENDIENTE;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getConversationRef() { return conversationRef; }
    public void setConversationRef(String conversationRef) { this.conversationRef = conversationRef; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
