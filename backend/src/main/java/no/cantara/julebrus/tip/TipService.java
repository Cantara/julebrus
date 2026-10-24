package no.cantara.julebrus.tip;

import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The tipping rules: three distinct beers that count, one tip each, nothing after the deadline. */
@Service
public class TipService {

    private final TipRepository tips;
    private final BeerRepository beers;
    private final SeasonProperties season;
    private final Clock clock;

    public TipService(TipRepository tips, BeerRepository beers, SeasonProperties season, Clock clock) {
        this.tips = tips;
        this.beers = beers;
        this.season = season;
        this.clock = clock;
    }

    public boolean closed() {
        return !clock.instant().isBefore(season.tipDeadline());
    }

    @Transactional(readOnly = true)
    public Optional<Tip> mine(UUID userId) {
        return tips.findById(userId);
    }

    @Transactional(readOnly = true)
    public long count() {
        return tips.count();
    }

    @Transactional(readOnly = true)
    public List<Tip> all() {
        return tips.findAll();
    }

    @Transactional
    public Tip place(UUID userId, UUID first, UUID second, UUID third) {
        requireOpen();
        List<UUID> picks = new java.util.ArrayList<>();
        for (UUID id : List.of(first, second, third)) {
            if (id == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TIP_BEER", "Tip three beers");
            picks.add(id);
        }
        Set<UUID> distinct = new HashSet<>(picks);
        if (distinct.size() != 3) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TIP_DUPLICATE", "Three different beers, please");
        }
        for (UUID id : picks) {
            Beer b = beers.findById(id).orElse(null);
            if (b == null || !b.counts()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TIP_BEER", "Only approved Vinmonopolet beers can win");
            }
        }
        Instant now = clock.instant();
        Tip tip = tips.findById(userId).map(t -> {
            t.change(first, second, third, now);
            return t;
        }).orElseGet(() -> tips.save(new Tip(userId, first, second, third, now)));
        return tip;
    }

    @Transactional
    public void withdraw(UUID userId) {
        requireOpen();
        tips.deleteById(userId);
    }

    private void requireOpen() {
        if (closed()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TIP_CLOSED", "Tipping closed when the last day began");
        }
    }
}
