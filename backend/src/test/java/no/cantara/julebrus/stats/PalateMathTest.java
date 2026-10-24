package no.cantara.julebrus.stats;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PalateMathTest {

    private final UUID b1 = UUID.randomUUID(), b2 = UUID.randomUUID(), b3 = UUID.randomUUID(), b4 = UUID.randomUUID();

    @Test
    void tooFewCommonBeersGivesNoAnswer() {
        assertThat(PalateMath.similarity(Map.of(b1, 5, b2, 3), Map.of(b1, 5, b2, 3), 3)).isNaN();
    }

    @Test
    void scoresThatMoveTogetherAreTwinsEvenOnDifferentLevels() {
        Map<UUID, Integer> generous = Map.of(b1, 6, b2, 5, b3, 4, b4, 6);
        Map<UUID, Integer> strict = Map.of(b1, 4, b2, 3, b3, 2, b4, 4);
        assertThat(PalateMath.similarity(generous, strict, 3)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void scoresThatMoveAgainstEachOtherAreOpposites() {
        Map<UUID, Integer> a = Map.of(b1, 6, b2, 2, b3, 5, b4, 1);
        Map<UUID, Integer> b = Map.of(b1, 1, b2, 5, b3, 2, b4, 6);
        assertThat(PalateMath.similarity(a, b, 3)).isLessThan(-0.9);
    }

    @Test
    void aFlatRaterIsComparedByClosenessInstead() {
        Map<UUID, Integer> flat = Map.of(b1, 4, b2, 4, b3, 4);
        assertThat(PalateMath.similarity(flat, Map.of(b1, 4, b2, 4, b3, 4), 3)).isEqualTo(1.0);
        assertThat(PalateMath.similarity(flat, Map.of(b1, 1, b2, 2, b3, 6), 3)).isLessThan(0.2);
    }

    @Test
    void tagLiftIsAgainstTheOwnMeanAndNeedsTwoUses() {
        List<Map.Entry<Set<String>, Integer>> t = List.of(
                Map.entry(Set.of("roasted"), 6), Map.entry(Set.of("roasted", "sweet"), 5),
                Map.entry(Set.of("sweet"), 2), Map.entry(Set.of("thin"), 1));
        List<PalateMath.TagLift> lifts = PalateMath.tagLifts(t, 2);
        // mean 3.5; roasted 5.5 -> +2; sweet 3.5 -> 0; thin used once -> dropped
        assertThat(lifts).extracting(PalateMath.TagLift::tag).containsExactly("roasted", "sweet");
        assertThat(lifts.get(0).lift()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(lifts.get(1).lift()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
