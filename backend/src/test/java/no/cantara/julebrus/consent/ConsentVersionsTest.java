package no.cantara.julebrus.consent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Versions are "year-n": 2026-10 is newer than 2026-7, and a new year beats any n. */
class ConsentVersionsTest {

    @Test
    void ordersByYearThenNumber() {
        assertThat(ConsentService.compareVersions("2026-10", "2026-7")).isPositive();
        assertThat(ConsentService.compareVersions("2026-7", "2026-7")).isZero();
        assertThat(ConsentService.compareVersions("2027-1", "2026-12")).isPositive();
        assertThat(ConsentService.compareVersions("2026-3", "2026-4")).isNegative();
    }
}
