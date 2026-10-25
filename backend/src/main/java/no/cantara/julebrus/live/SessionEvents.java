package no.cantara.julebrus.live;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Live reveal: one server-sent event stream per table. Every write to a session (a
 * score, a duel, someone joining, a beer added, a blind guess) publishes a small event,
 * and every phone at the table re-reads the session at once, so the paper tears off on
 * all of them together. Events carry no data beyond their kind: the client fetches, so
 * the blind-reveal rules stay in one place.
 *
 * <p>One more channel, {@link #FEED}, is the whole house: every table event is mirrored
 * there, and so is everything else that shows up in "I kveld" (a solo roll, an approved
 * photo, a cheer), so the day's wall updates on every phone that has it open.
 */
@Component
public class SessionEvents {

    /** What changed. The client treats every kind the same way (refetch), the names are for logs and tests. */
    public enum Kind { SESSION, TASTING, DUEL, BLIND, PHOTO, REACTION }

    /** The day-wide channel behind /api/feed/events. */
    public static final UUID FEED = new UUID(0L, 0L);

    /** Browsers reconnect on their own; a shortish timeout keeps dead streams from piling up. */
    static final Duration TIMEOUT = Duration.ofMinutes(30);
    static final Duration HEARTBEAT = Duration.ofSeconds(20);

    private final Map<UUID, List<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SessionEvents() {
        heartbeat.scheduleAtFixedRate(this::beat, HEARTBEAT.toSeconds(), HEARTBEAT.toSeconds(), TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        List<SseEmitter> list = streams.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> {
            list.remove(emitter);
            if (list.isEmpty()) streams.remove(sessionId, list);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("hello").data(sessionId.toString()));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * Tells the table something changed, and the feed with it. Inside a transaction the
     * event waits for the commit, so a client that refetches on it sees the new row.
     * A null table (a solo roll) reaches the feed alone.
     */
    public void publish(UUID sessionId, Kind kind) {
        Runnable fire = () -> {
            if (sessionId != null) send(sessionId, kind);
            send(FEED, kind);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fire.run();
                }
            });
        } else {
            fire.run();
        }
    }

    /** Something that is not about one table changed: a photo approved, a cheer given. */
    public void publishFeed(Kind kind) {
        publish(null, kind);
    }

    /** How many phones are listening at this table right now. */
    public int listeners(UUID sessionId) {
        List<SseEmitter> list = streams.get(sessionId);
        return list == null ? 0 : list.size();
    }

    private void send(UUID sessionId, Kind kind) {
        List<SseEmitter> list = streams.get(sessionId);
        if (list == null) return;
        String name = kind.name().toLowerCase(java.util.Locale.ROOT);
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name(name).data(name));
            } catch (Exception ex) {
                e.completeWithError(ex);
            }
        }
    }

    private void beat() {
        for (List<SseEmitter> list : streams.values()) {
            for (SseEmitter e : list) {
                try {
                    e.send(SseEmitter.event().comment("keepalive"));
                } catch (Exception ex) {
                    e.completeWithError(ex);
                }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        streams.values().forEach(list -> list.forEach(SseEmitter::complete));
    }
}
