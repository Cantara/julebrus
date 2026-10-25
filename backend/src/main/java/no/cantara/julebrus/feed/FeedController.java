package no.cantara.julebrus.feed;

import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.live.SessionEvents;
import no.cantara.julebrus.user.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** "I kveld": the day's wall and the cheers on it. Anyone signed in and consented. */
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    public record ReactRequest(@NotNull FeedService.Target kind, @NotNull UUID targetId, @NotNull String emoji) {
    }

    private final FeedService feed;
    private final SessionEvents events;
    private final CurrentUser currentUser;

    public FeedController(FeedService feed, SessionEvents events, CurrentUser currentUser) {
        this.feed = feed;
        this.events = events;
        this.currentUser = currentUser;
    }

    /** The wall for one Oslo day, today when none is given. */
    @GetMapping
    public FeedService.FeedView view(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return feed.view(currentUser.get(), day == null ? feed.today() : day);
    }

    /** Toggles one emoji on one thing; answers with the thing's new tally. */
    @PutMapping("/reactions")
    public List<FeedService.ReactionView> react(@RequestBody ReactRequest in) {
        return feed.react(currentUser.get(), in.kind(), in.targetId(), in.emoji());
    }

    /** The day-wide stream: a kind-only event for every change anywhere, the client refetches. */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events() {
        currentUser.get();
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(events.subscribe(SessionEvents.FEED));
    }
}
