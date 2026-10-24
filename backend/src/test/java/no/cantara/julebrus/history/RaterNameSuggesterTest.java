package no.cantara.julebrus.history;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RaterNameSuggesterTest {

    @Test
    void exactFirstNameRanksBeforePrefixAndNoMatchLast() {
        assertThat(RaterNameSuggester.rank("Eirik Larsen", "Eirik")).isEqualTo(0);
        assertThat(RaterNameSuggester.rank("Eirik Larsen", "Eirik L")).isEqualTo(1);
        assertThat(RaterNameSuggester.rank("Eirik Larsen", "Eirik Litangen")).isEqualTo(1);
        assertThat(RaterNameSuggester.rank("Eirik Larsen", "Totto")).isEqualTo(2);
    }

    @Test
    void caseAndAccentsAreIgnored() {
        assertThat(RaterNameSuggester.suggested("JOSÉ García", "jose")).isTrue();
        assertThat(RaterNameSuggester.suggested("Jose Garcia", "José B")).isTrue();
        assertThat(RaterNameSuggester.suggested("  eirik   larsen ", "EIRIK")).isTrue();
    }

    @Test
    void emptyDisplayNameSuggestsNothing() {
        assertThat(RaterNameSuggester.suggested("", "Eirik")).isFalse();
        assertThat(RaterNameSuggester.suggested(null, "Eirik")).isFalse();
    }

    @Test
    void surnameAloneIsNotASuggestion() {
        assertThat(RaterNameSuggester.suggested("Eirik Larsen", "Larsen")).isFalse();
        assertThat(List.of("Totto", "Eirik L", "Eirik").stream()
                .sorted(java.util.Comparator.comparingInt(n -> RaterNameSuggester.rank("Eirik Larsen", n))).toList())
                .containsExactly("Eirik", "Eirik L", "Totto");
    }
}
