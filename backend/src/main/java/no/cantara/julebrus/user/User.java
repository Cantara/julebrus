package no.cantara.julebrus.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When the team choice was made, "later" included; null means the team page is still due. */
    @Column(name = "teams_chosen_at")
    private Instant teamsChosenAt;

    protected User() {
    }

    public User(String email, String displayName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getTeamsChosenAt() { return teamsChosenAt; }
    public void setTeamsChosenAt(Instant at) { this.teamsChosenAt = at; }
}
