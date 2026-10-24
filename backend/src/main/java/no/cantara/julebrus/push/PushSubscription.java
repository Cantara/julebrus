package no.cantara.julebrus.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One browser that said yes to notifications. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_nudge_day")
    private LocalDate lastNudgeDay;

    @Column(name = "finale_sent_at")
    private Instant finaleSentAt;

    @Column(nullable = false)
    private int failures;

    protected PushSubscription() {
    }

    public PushSubscription(UUID userId, String endpoint, String p256dh, String auth, String userAgent, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
        this.createdAt = now;
    }

    public void rekey(UUID userId, String p256dh, String auth, String userAgent) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
        this.failures = 0;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public LocalDate getLastNudgeDay() { return lastNudgeDay; }
    public void setLastNudgeDay(LocalDate day) { this.lastNudgeDay = day; }
    public Instant getFinaleSentAt() { return finaleSentAt; }
    public void setFinaleSentAt(Instant at) { this.finaleSentAt = at; }
    public int getFailures() { return failures; }
    public void setFailures(int failures) { this.failures = failures; }
}
