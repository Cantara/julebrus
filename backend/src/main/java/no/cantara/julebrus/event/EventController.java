package no.cantara.julebrus.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.session.SessionRepository;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Planned tastings. Names of attendees are visible to everyone with consent, like a table's members. */
@RestController
@RequestMapping("/api/events")
public class EventController {

    static final String NO_CONTROL = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$";

    public record NewEvent(@NotBlank @Size(max = 120) @Pattern(regexp = NO_CONTROL) String title,
                           @NotNull Instant startsAt, boolean online,
                           @Size(max = 200) @Pattern(regexp = NO_CONTROL) String place,
                           @Size(max = 1000) String note,
                           @Min(2) @Max(EventService.MAX_CAPACITY) Integer capacity) {
    }

    public record AttendeeView(UUID userId, String displayName) {
    }

    /** {@code joinCode} only once the host has opened the table, and only for attendees. */
    public record EventView(UUID id, String title, Instant startsAt, boolean online, String place, String note, Integer capacity,
                            UUID hostId, String hostName, boolean mine, boolean joined, boolean cancelled,
                            List<AttendeeView> attendees, UUID sessionId, String joinCode, boolean tableOpen) {
    }

    private final EventService service;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final ConsentService consents;

    public EventController(EventService service, CurrentUser currentUser, UserRepository users, SessionRepository sessions,
                           ConsentService consents) {
        this.service = service;
        this.currentUser = currentUser;
        this.users = users;
        this.sessions = sessions;
        this.consents = consents;
    }

    @GetMapping
    public List<EventView> upcoming() {
        User me = currentUser.get();
        List<Event> list = service.upcoming();
        Set<UUID> ids = new HashSet<>();
        list.forEach(e -> { ids.add(e.getHostId()); e.getAttendees().forEach(a -> ids.add(a.userId())); });
        Map<UUID, String> names = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        Set<UUID> active = consents.activeUserIds();
        return list.stream().map(e -> view(e, me, names, active)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventView create(@Valid @RequestBody NewEvent in) {
        User me = currentUser.get();
        return view(service.create(me, in.title(), in.startsAt(), in.online(), in.place(), in.note(), in.capacity()), me);
    }

    @PostMapping("/{id}/join")
    public EventView join(@PathVariable UUID id) {
        User me = currentUser.get();
        return view(service.join(id, me), me);
    }

    @DeleteMapping("/{id}/join")
    public EventView leave(@PathVariable UUID id) {
        User me = currentUser.get();
        return view(service.leave(id, me), me);
    }

    @PostMapping("/{id}/cancel")
    public EventView cancel(@PathVariable UUID id) {
        User me = currentUser.get();
        return view(service.cancel(id, me), me);
    }

    @PostMapping("/{id}/table")
    public EventView openTable(@PathVariable UUID id) {
        User me = currentUser.get();
        return view(service.openTable(id, me), me);
    }

    private EventView view(Event e, User me) {
        Set<UUID> ids = new HashSet<>();
        ids.add(e.getHostId());
        e.getAttendees().forEach(a -> ids.add(a.userId()));
        Map<UUID, String> names = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        return view(e, me, names, consents.activeUserIds());
    }

    private EventView view(Event e, User me, Map<UUID, String> names, Set<UUID> active) {
        boolean joined = e.isAttending(me.getId());
        List<AttendeeView> who = e.getAttendees().stream()
                .filter(a -> active.contains(a.userId()))
                .map(a -> new AttendeeView(a.userId(), names.getOrDefault(a.userId(), "?"))).toList();
        TastingSession table = e.getSessionId() == null ? null : sessions.findById(e.getSessionId()).orElse(null);
        return new EventView(e.getId(), e.getTitle(), e.getStartsAt(), e.isOnline(), e.getPlace(), e.getNote(), e.getCapacity(),
                e.getHostId(), names.getOrDefault(e.getHostId(), "?"), e.getHostId().equals(me.getId()), joined, e.isCancelled(),
                who, e.getSessionId(), joined && table != null && table.isOpen() ? table.getJoinCode() : null,
                table != null && table.isOpen());
    }
}
