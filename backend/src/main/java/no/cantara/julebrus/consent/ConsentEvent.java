package no.cantara.julebrus.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit trail of consent: every accept and withdraw is one row. */
@Entity
@Table(name = "consent_events")
public class ConsentEvent {

    public enum Kind { ACCEPT, WITHDRAW }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "consent_version", nullable = false)
    private String consentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false)
    private Kind kind;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected ConsentEvent() {
    }

    public ConsentEvent(UUID userId, String consentVersion, Kind kind) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.consentVersion = consentVersion;
        this.kind = kind;
        this.at = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getConsentVersion() { return consentVersion; }
    public Kind getKind() { return kind; }
    public Instant getAt() { return at; }
}
