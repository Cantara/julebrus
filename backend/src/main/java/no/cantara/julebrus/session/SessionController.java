package no.cantara.julebrus.session;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.blind.BlindService;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.team.TeamService;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    public record MemberView(UUID userId, String displayName, List<String> teams) {
    }

    /**
     * Blind reveal: {@code scores} is only populated once every member has rated the
     * beer. Until then members see who has rated, not what.
     */
    public record SessionBeerView(UUID beerId, String name, String brewery, List<UUID> ratedBy,
                                  boolean revealed, Map<UUID, Integer> scores, Double average) {
    }

    public record SessionView(UUID id, UUID hostId, String joinCode, Instant createdAt, Instant closedAt,
                              boolean solo, List<MemberView> members, List<SessionBeerView> beers) {
    }

    public record JoinRequest(@NotBlank String code) {
    }

    public record AddBeerRequest(@NotNull UUID beerId) {
    }

    private final SessionService service;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final BeerRepository beers;
    private final TastingRepository tastings;
    private final BlindService blind;
    private final TeamService teams;

    public SessionController(SessionService service, CurrentUser currentUser, UserRepository users,
                             BeerRepository beers, TastingRepository tastings, BlindService blind, TeamService teams) {
        this.service = service;
        this.currentUser = currentUser;
        this.users = users;
        this.beers = beers;
        this.tastings = tastings;
        this.blind = blind;
        this.teams = teams;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView open() {
        return view(service.open(currentUser.get().getId()));
    }

    @PostMapping("/join")
    public SessionView join(@Valid @RequestBody JoinRequest in) {
        return view(service.join(currentUser.get().getId(), in.code()));
    }

    @GetMapping("/{id}")
    public SessionView get(@PathVariable UUID id) {
        return view(service.getForMember(id, currentUser.get().getId()));
    }

    @PostMapping("/{id}/beers")
    public SessionView addBeer(@PathVariable UUID id, @Valid @RequestBody AddBeerRequest in) {
        if (!beers.existsById(in.beerId())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown beer");
        return view(service.addBeer(id, currentUser.get().getId(), in.beerId()));
    }

    @PostMapping("/{id}/close")
    public SessionView close(@PathVariable UUID id) {
        SessionView closed = view(service.close(id, currentUser.get().getId()));
        blind.revealAllOpen(id);
        return closed;
    }

    private SessionView view(TastingSession s) {
        List<UUID> memberIds = s.getMembers().stream().map(TastingSession.Member::userId).toList();
        Map<UUID, User> userById = users.findAllById(memberIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, List<String>> teamsOf = teams.slugsOf(memberIds);
        List<MemberView> members = memberIds.stream()
                .map(id -> new MemberView(id, userById.containsKey(id) ? userById.get(id).getDisplayName() : "?",
                        teamsOf.getOrDefault(id, List.of())))
                .toList();

        List<UUID> beerIds = s.getBeers().stream().map(TastingSession.SessionBeer::beerId).toList();
        Map<UUID, Beer> beerById = beers.findAllById(beerIds).stream()
                .collect(Collectors.toMap(Beer::getId, Function.identity()));
        Map<UUID, List<Tasting>> tastingsByBeer = tastings.findBySessionId(s.getId()).stream()
                .collect(Collectors.groupingBy(Tasting::getBeerId));

        List<SessionBeerView> beerViews = beerIds.stream().map(beerId -> {
            Beer beer = beerById.get(beerId);
            List<Tasting> rated = tastingsByBeer.getOrDefault(beerId, List.of());
            List<UUID> ratedBy = rated.stream().map(Tasting::getUserId).toList();
            boolean revealed = !s.isOpen() || ratedBy.containsAll(memberIds);
            Map<UUID, Integer> scores = revealed
                    ? rated.stream().collect(Collectors.toMap(Tasting::getUserId, Tasting::getScore))
                    : null;
            Double average = revealed && !rated.isEmpty()
                    ? rated.stream().mapToInt(Tasting::getScore).average().orElse(Double.NaN)
                    : null;
            return new SessionBeerView(beerId, beer == null ? "?" : beer.getName(),
                    beer == null ? null : beer.getBrewery(), ratedBy, revealed, scores, average);
        }).toList();

        return new SessionView(s.getId(), s.getHostId(), s.getJoinCode(), s.getCreatedAt(), s.getClosedAt(),
                s.isSolo(), members, beerViews);
    }
}
