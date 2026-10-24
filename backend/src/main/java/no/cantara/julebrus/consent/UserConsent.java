package no.cantara.julebrus.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One person's answer to one version of the consent notice. */
@Entity
@Table(name = "user_consents")
public class UserConsent {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "consent_version", nullable = false)
    private String consentVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected UserConsent() {
    }

    public UserConsent(UUID userId, String consentVersion) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.consentVersion = consentVersion;
        this.acceptedAt = Instant.now();
    }

    /** Accepting again after a withdrawal reuses the row. */
    public void accept() {
        this.acceptedAt = Instant.now();
        this.withdrawnAt = null;
    }

    public void withdraw() {
        this.withdrawnAt = Instant.now();
    }

    public boolean active() {
        return withdrawnAt == null;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getConsentVersion() { return consentVersion; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
