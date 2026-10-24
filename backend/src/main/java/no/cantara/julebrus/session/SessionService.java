package no.cantara.julebrus.session;

import no.cantara.julebrus.live.SessionEvents;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.UUID;

@Service
public class SessionService {

    /** No 0/O/1/I so the code survives a dim kitchen. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionRepository sessions;
    private final SessionEvents events;

    public SessionService(SessionRepository sessions, SessionEvents events) {
        this.sessions = sessions;
        this.events = events;
    }

    @Transactional
    public TastingSession open(UUID hostId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            if (sessions.findByJoinCode(code).isEmpty()) {
                return sessions.save(new TastingSession(hostId, code));
            }
        }
        throw new IllegalStateException("Could not allocate a unique join code");
    }

    @Transactional
    public TastingSession join(UUID userId, String code) {
        TastingSession session = sessions.findByJoinCode(normalise(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No session with that code"));
        if (!session.isOpen()) {
            throw new ResponseStatusException(HttpStatus.GONE, "That session is closed");
        }
        session.join(userId);
        events.publish(session.getId(), SessionEvents.Kind.SESSION);
        return session;
    }

    @Transactional(readOnly = true)
    public java.util.List<TastingSession> mine(UUID userId) {
        return sessions.findByMember(userId);
    }

    @Transactional(readOnly = true)
    public TastingSession getForMember(UUID sessionId, UUID userId) {
        TastingSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!session.isMember(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this session");
        }
        return session;
    }

    @Transactional
    public TastingSession addBeer(UUID sessionId, UUID userId, UUID beerId) {
        TastingSession session = getForMember(sessionId, userId);
        if (!session.isOpen()) throw new ResponseStatusException(HttpStatus.GONE, "That session is closed");
        session.addBeer(beerId);
        events.publish(sessionId, SessionEvents.Kind.SESSION);
        return session;
    }

    @Transactional
    public TastingSession close(UUID sessionId, UUID userId) {
        TastingSession session = getForMember(sessionId, userId);
        if (!session.getHostId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can close a session");
        }
        session.close();
        events.publish(sessionId, SessionEvents.Kind.SESSION);
        return session;
    }

    static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase().replace('0', 'O').replace('1', 'I');
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
