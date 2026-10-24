package no.cantara.julebrus.badge;

import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.session.SessionRepository;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    public record BadgeView(Badge id, String title, String description, boolean earned) {
    }

    private final TastingRepository tastings;
    private final SessionRepository sessions;
    private final CurrentUser currentUser;
    private final BadgeEvaluator evaluator;

    public BadgeController(TastingRepository tastings, SessionRepository sessions, CurrentUser currentUser,
                           SeasonProperties season) {
        this.tastings = tastings;
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.evaluator = new BadgeEvaluator(season);
    }

    @GetMapping("/me")
    public List<BadgeView> mine() {
        UUID me = currentUser.get().getId();
        List<Tasting> mine = tastings.findByUserIdOrderByTastedAtDesc(me);

        Map<UUID, Long> othersBefore = new HashMap<>();
        Map<UUID, Double> crowdMean = new HashMap<>();
        Map<UUID, Double> groupAverage = new HashMap<>();
        for (Tasting t : mine) {
            List<Tasting> all = tastings.findByBeerId(t.getBeerId());
            othersBefore.put(t.getBeerId(), all.stream()
                    .filter(o -> !o.getUserId().equals(me) && o.getTastedAt().isBefore(t.getTastedAt())).count());
            all.stream().filter(o -> !o.getUserId().equals(me)).mapToInt(Tasting::getScore).average()
                    .ifPresent(m -> crowdMean.put(t.getBeerId(), m));
            if (t.getSessionId() != null) {
                all.stream().filter(o -> t.getSessionId().equals(o.getSessionId())).mapToInt(Tasting::getScore)
                        .average().ifPresent(m -> groupAverage.put(t.getBeerId(), m));
            }
        }

        Set<UUID> coTasters = new HashSet<>();
        mine.stream().map(Tasting::getSessionId).filter(java.util.Objects::nonNull).distinct()
                .map(sessions::findById).flatMap(java.util.Optional::stream)
                .flatMap(s -> s.getMembers().stream()).map(TastingSession.Member::userId)
                .filter(id -> !id.equals(me)).forEach(coTasters::add);

        int soloDays = tastings.findSoloDays(me).size();
        Set<Badge> earned = evaluator.evaluate(new BadgeEvaluator.Context(
                mine, soloDays, coTasters, othersBefore, crowdMean, groupAverage));

        return java.util.Arrays.stream(Badge.values())
                .map(b -> new BadgeView(b, b.title, b.description, earned.contains(b)))
                .toList();
    }
}
