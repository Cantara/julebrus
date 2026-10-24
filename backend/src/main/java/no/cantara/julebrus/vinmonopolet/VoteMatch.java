package no.cantara.julebrus.vinmonopolet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One (year, beer) entry of the tasting results, the machine's guess at which Vinmonopolet beer it is, and the
 * administrator's decision on it. The machine_* columns and candidates are overwritten by the importer when a newer
 * vote_matches.csv differs; the decision columns are written only by a person.
 */
@Entity
@Table(name = "vote_matches")
public class VoteMatch {

    public enum Decision { CONFIRMED, CHOSEN, NONE, NOT_A_PRODUCT }

    @Id
    private UUID id;

    @Column(name = "season_year", nullable = false)
    private int year;

    @Column(name = "vote_beer", nullable = false)
    private String voteBeer;

    @Column(name = "machine_status", nullable = false)
    private String machineStatus;
    @Column(name = "machine_method")
    private String machineMethod;
    @Column(name = "machine_score")
    private BigDecimal machineScore;
    @Column(name = "machine_article_no")
    private String machineArticleNo;
    @Column(name = "machine_article_options")
    private String machineArticleOptions;
    @Column(name = "machine_product_name")
    private String machineProductName;
    @Column(name = "machine_list_year")
    private String machineListYear;
    private String candidates;

    @Enumerated(EnumType.STRING)
    private Decision decision;
    @Column(name = "decision_article_no")
    private String decisionArticleNo;
    @Column(name = "decision_product_name")
    private String decisionProductName;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Optimistic lock: two admins on one row, or an import during a decision, cannot both win. */
    @Version
    private long version;

    protected VoteMatch() {
    }

    public VoteMatch(int year, String voteBeer) {
        this.id = UUID.randomUUID();
        this.year = year;
        this.voteBeer = voteBeer;
    }

    /** Sets the machine side; returns true if anything differed. Never touches the decision. */
    public boolean applyMachine(String status, String method, BigDecimal score, String articleNo, String options,
                                String productName, String listYear, String candidates) {
        boolean changed = !Objects.equals(machineStatus, status) || !Objects.equals(machineMethod, method)
                || !sameNumber(machineScore, score) || !Objects.equals(machineArticleNo, articleNo)
                || !Objects.equals(machineArticleOptions, options) || !Objects.equals(machineProductName, productName)
                || !Objects.equals(machineListYear, listYear) || !Objects.equals(this.candidates, candidates);
        if (changed) {
            this.machineStatus = status;
            this.machineMethod = method;
            this.machineScore = score;
            this.machineArticleNo = articleNo;
            this.machineArticleOptions = options;
            this.machineProductName = productName;
            this.machineListYear = listYear;
            this.candidates = candidates;
        }
        return changed;
    }

    private static boolean sameNumber(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    public void decide(Decision d, String articleNo, String productName, UUID by, Instant at) {
        this.decision = d;
        this.decisionArticleNo = articleNo;
        this.decisionProductName = productName;
        this.decidedBy = by;
        this.decidedAt = at;
    }

    public void clearDecision() {
        this.decision = null;
        this.decisionArticleNo = null;
        this.decisionProductName = null;
        this.decidedBy = null;
        this.decidedAt = null;
    }

    public boolean isDecided() {
        return decision != null;
    }

    public UUID getId() { return id; }
    public int getYear() { return year; }
    public String getVoteBeer() { return voteBeer; }
    public String getMachineStatus() { return machineStatus; }
    public String getMachineMethod() { return machineMethod; }
    public BigDecimal getMachineScore() { return machineScore; }
    public String getMachineArticleNo() { return machineArticleNo; }
    public String getMachineArticleOptions() { return machineArticleOptions; }
    public String getMachineProductName() { return machineProductName; }
    public String getMachineListYear() { return machineListYear; }
    public String getCandidates() { return candidates; }
    public Decision getDecision() { return decision; }
    public String getDecisionArticleNo() { return decisionArticleNo; }
    public String getDecisionProductName() { return decisionProductName; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public long getVersion() { return version; }
}
