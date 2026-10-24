package no.cantara.julebrus.live;

import no.cantara.julebrus.session.SessionService;
import no.cantara.julebrus.user.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
public class SessionEventsController {

    private final SessionEvents events;
    private final SessionService sessions;
    private final CurrentUser currentUser;

    public SessionEventsController(SessionEvents events, SessionService sessions, CurrentUser currentUser) {
        this.events = events;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    /** Members only. The headers tell proxies not to buffer the stream. */
    @GetMapping(value = "/api/sessions/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable UUID id) {
        sessions.getForMember(id, currentUser.get().getId());
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(events.subscribe(id));
    }
}
