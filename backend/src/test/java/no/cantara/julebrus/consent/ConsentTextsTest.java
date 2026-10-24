package no.cantara.julebrus.consent;

import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.SponsorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentTextsTest {

    @Test
    void contactAndSponsorAreFilledIn() {
        var texts = new ConsentService(null, new SponsorProperties("eXOReaction", "https://exoreaction.com"),
                new ContactProperties("julebrus@cantara.no")).texts();
        assertThat(texts.nb()).contains("eXOReaction").contains("julebrus@cantara.no").doesNotContain("{");
        assertThat(texts.en()).contains("eXOReaction").contains("julebrus@cantara.no").doesNotContain("{");
    }

    @Test
    void blankContactFallsBackToTheOrganisersAndBlankSponsorDropsItsSentence() {
        var texts = new ConsentService(null, new SponsorProperties("", ""), new ContactProperties("")).texts();
        assertThat(texts.nb()).contains("kontakt arrangørene").doesNotContain("eXOReaction").doesNotContain("{");
        assertThat(texts.en()).contains("contact the organisers").doesNotContain("eXOReaction").doesNotContain("{");
    }

    @Test
    void onlyHttpLinksAreExposedAsSponsorUrl() {
        assertThat(new SponsorProperties("x", "javascript:alert(1)").safeUrl()).isNull();
        assertThat(new SponsorProperties("x", "https://example.com").safeUrl()).isEqualTo("https://example.com");
        assertThat(new SponsorProperties(" ", "https://example.com").visible()).isFalse();
    }
}
