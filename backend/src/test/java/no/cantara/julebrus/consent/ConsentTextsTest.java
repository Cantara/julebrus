package no.cantara.julebrus.consent;

import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.SponsorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentTextsTest {

    @Test
    void contactAndSponsorAreFilledIn() {
        var texts = new ConsentService(null, null, new SponsorProperties("eXOReaction", "https://exoreaction.com"),
                new ContactProperties("julebrus@cantara.no")).texts();
        assertThat(texts.nb()).contains("eXOReaction").contains("julebrus@cantara.no").doesNotContain("{");
        assertThat(texts.en()).contains("eXOReaction").contains("julebrus@cantara.no").doesNotContain("{");
    }

    @Test
    void blankContactFallsBackToTheOrganisersAndBlankSponsorDropsItsSentence() {
        var texts = new ConsentService(null, null, new SponsorProperties("", ""), new ContactProperties("")).texts();
        assertThat(texts.nb()).contains("ta kontakt med arrangørene").doesNotContain("eXOReaction").doesNotContain("{");
        assertThat(texts.en()).contains("get in touch with the organisers").doesNotContain("eXOReaction").doesNotContain("{");
    }

    @Test
    void blankSponsorKeepsTheIdentitySentenceAndBlankContactDoesToo() {
        var noSponsor = new ConsentService(null, null, new SponsorProperties(" ", ""), new ContactProperties("a@b.no")).texts();
        assertThat(noSponsor.nb()).contains("Den drives av Cantara-miljøet.").doesNotContain("sponser");
        assertThat(noSponsor.en()).contains("It is run by the Cantara community.").doesNotContain("sponsors");
        var noContact = new ConsentService(null, null, new SponsorProperties("eXOReaction", ""), new ContactProperties(null)).texts();
        assertThat(noContact.nb()).contains("Den drives av Cantara-miljøet.").contains("eXOReaction sponser Cantara");
        assertThat(noContact.en()).contains("It is run by the Cantara community.").contains("eXOReaction sponsors Cantara");
    }

    @Test
    void textsDescribeWhatWithdrawalDoesAndNoLongerSayFirstNames() {
        var texts = new ConsentService(null, null, new SponsorProperties("x", ""), new ContactProperties("a@b.no")).texts();
        assertThat(texts.nb()).contains("slettes ikke").doesNotContain("fornavn");
        assertThat(texts.en()).contains("does not delete").doesNotContain("first names").doesNotContainIgnoringCase("draft");
    }

    @Test
    void onlyHttpLinksAreExposedAsSponsorUrl() {
        assertThat(new SponsorProperties("x", "javascript:alert(1)").safeUrl()).isNull();
        assertThat(new SponsorProperties("x", "https://example.com").safeUrl()).isEqualTo("https://example.com");
        assertThat(new SponsorProperties(" ", "https://example.com").visible()).isFalse();
    }
}
