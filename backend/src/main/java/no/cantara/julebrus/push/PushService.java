package no.cantara.julebrus.push;

import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.event.Event;
import no.cantara.julebrus.event.EventRepository;
import no.cantara.julebrus.advent.AdventService;
import no.cantara.julebrus.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Subscriptions and the three messages the app sends: the daily "dagens luke" nudge to
 * people who have not opened today's advent door, with the door's question, the reminder an hour before a planned tasting to those
 * signed up, and the finale announcement at the reveal.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    /** After this many consecutive delivery failures a browser is forgotten. */
    static final int MAX_FAILURES = 5;

    private final PushSubscriptionRepository subscriptions;
    private final AdventService advent;
    private final EventRepository events;
    private final WebPushClient client;
    private final PushProperties props;
    private final SeasonProperties season;
    private final ContactProperties contact;
    private final Clock clock;

    public PushService(PushSubscriptionRepository subscriptions, AdventService advent, EventRepository events,
                       WebPushClient client, PushProperties props, SeasonProperties season, ContactProperties contact, Clock clock) {
        this.subscriptions = subscriptions;
        this.advent = advent;
        this.events = events;
        this.client = client;
        this.props = props;
        this.season = season;
        this.contact = contact;
        this.clock = clock;
    }

    public boolean enabled() {
        return props.enabled();
    }

    @EventListener(ApplicationReadyEvent.class)
    void checkKeys() throws Exception {
        if (!props.enabled()) {
            log.info("Web Push is off: no VAPID keys configured");
            return;
        }
        client.verifyKeys();
        log.info("Web Push is on, nudge at {}", props.nudgeTime());
    }

    @Transactional(readOnly = true)
    public List<PushSubscription> mine(UUID userId) {
        return subscriptions.findByUserId(userId);
    }

    @Transactional
    public PushSubscription subscribe(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        requireEnabled();
        if (endpoint == null || !endpoint.startsWith("https://") || p256dh == null || auth == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PUSH_SUBSCRIPTION", "Not a push subscription");
        }
        try {
            WebPushCrypto.publicKey(WebPushCrypto.b64(p256dh));
            WebPushCrypto.b64(auth);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PUSH_SUBSCRIPTION", "The browser's keys do not parse");
        }
        String agent = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        return subscriptions.findByEndpoint(endpoint).map(s -> {
            s.rekey(userId, p256dh, auth, agent);
            return s;
        }).orElseGet(() -> subscriptions.save(new PushSubscription(userId, endpoint, p256dh, auth, agent, clock.instant())));
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        subscriptions.findByEndpoint(endpoint).filter(s -> s.getUserId().equals(userId)).ifPresent(subscriptions::delete);
    }

    /** A "varsler er på" message to the person's own browsers, so they see it works. */
    @Transactional
    public int sendTest(UUID userId) {
        requireEnabled();
        int sent = 0;
        for (PushSubscription s : subscriptions.findByUserId(userId)) {
            if (deliver(s, payload("Varsler er på", "Du får et lite dytt klokka " + props.nudgeTime() + " de dagene luka står uåpnet, og en time før en smaking du er påmeldt.", "/", "test"))) sent++;
        }
        return sent;
    }

    /** Every minute: which planned tastings are an hour away, who is due a nudge, and whether the finale is due. Cheap when nothing is. */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void tick() {
        if (!props.enabled()) return;
        try {
            Instant now = clock.instant();
            remindEvents(now);
            ZonedDateTime local = now.atZone(season.zone());
            LocalDate today = local.toLocalDate();
            LocalTime time = local.toLocalTime();
            String question = advent.todayQuestion();
            String nudgeTitle = "Luke %d står uåpnet".formatted(today.toEpochDay() - season.start().toEpochDay() + 1);
            String nudgeBody = question == null ? "Ett trykk, ett spørsmål. Hva står bak dagens luke?" : question;
            for (PushSubscription s : subscriptions.findAll()) {
                if (PushPlanner.finaleDue(now, season.revealAt(), s.getCreatedAt(), s.getFinaleSentAt())) {
                    s.setFinaleSentAt(now);
                    deliver(s, payload("Stjernen er tent", "Cantaras juleøl " + season.year() + " er kåret. Se pallen og årets priser.", "/finale", "finale"));
                    continue;
                }
                boolean opened = advent.openedToday(s.getUserId());
                if (PushPlanner.nudgeDue(today, time, props.nudgeTime(), season.start(), season.end(), s.getLastNudgeDay(), opened)) {
                    s.setLastNudgeDay(today);
                    deliver(s, payload(nudgeTitle, nudgeBody, "/luke/" + today, "nudge-" + today));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Push tick failed: {}", e.toString());
        }
    }

    /** "Smaking om en time" to every attendee's browsers, once per event, {@link PushPlanner#EVENT_LEAD} before it starts. */
    private void remindEvents(Instant now) {
        for (Event e : events.findByStartsAtBetweenAndCancelledAtIsNullAndReminderSentAtIsNull(now, now.plus(PushPlanner.EVENT_LEAD))) {
            if (!PushPlanner.eventReminderDue(now, e.getStartsAt(), e.getCreatedAt(), e.getReminderSentAt(), e.isCancelled())) continue;
            e.markReminded(now);
            String where = e.isOnline() ? "digitalt" : e.getPlace() == null ? "" : e.getPlace();
            int n = e.getAttendees().size();
            String body = e.getTitle() + (where.isEmpty() ? "" : ", " + where) + ". " + n + (n == 1 ? " påmeldt." : " påmeldte.")
                    + " Verten åpner bordet fra Smakinger.";
            String payload = payload("Smaking om en time", body, "/smakinger", "event-" + e.getId());
            for (Event.Attendee a : e.getAttendees()) {
                for (PushSubscription s : subscriptions.findByUserId(a.userId())) deliver(s, payload);
            }
        }
    }

    /** Sends, and forgets a browser the push service says is gone or that keeps failing. */
    boolean deliver(PushSubscription s, String payload) {
        try {
            int status = client.send(s, payload, subject());
            if (status == 404 || status == 410) {
                log.info("Push subscription gone ({}), removing", status);
                subscriptions.delete(s);
                return false;
            }
            if (status >= 400) {
                s.setFailures(s.getFailures() + 1);
                log.warn("Push service answered {} for one subscription (failure {})", status, s.getFailures());
                if (s.getFailures() >= MAX_FAILURES) subscriptions.delete(s);
                return false;
            }
            s.setFailures(0);
            return true;
        } catch (Exception e) {
            s.setFailures(s.getFailures() + 1);
            log.warn("Push delivery failed: {} (failure {})", e.toString(), s.getFailures());
            if (s.getFailures() >= MAX_FAILURES) subscriptions.delete(s);
            return false;
        }
    }

    String subject() {
        if (props.subject() != null && !props.subject().isBlank()) return props.subject();
        String email = contact.email();
        return email == null || email.isBlank() ? "https://xmas.cantara.no" : "mailto:" + email;
    }

    static String payload(String title, String body, String url, String tag) {
        return "{\"title\":" + json(title) + ",\"body\":" + json(body) + ",\"url\":" + json(url) + ",\"tag\":" + json(tag) + "}";
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void requireEnabled() {
        if (!props.enabled()) throw new ApiException(HttpStatus.NOT_FOUND, "PUSH_OFF", "Notifications are not configured");
    }
}
