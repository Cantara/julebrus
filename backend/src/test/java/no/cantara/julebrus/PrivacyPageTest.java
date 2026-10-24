package no.cantara.julebrus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** /privacy is plain server-rendered HTML: it works for a crawler that does not run JavaScript. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PrivacyPageTest {

    @Autowired
    MockMvc mvc;

    @Test
    void servedAsHtmlWithBothLanguagesAndNoScripts() throws Exception {
        for (String path : new String[]{"/privacy", "/privacy/"}) {
            var res = mvc.perform(get(path).header("X-Dev-User", "never-consented@cantara.no")).andReturn().getResponse();
            assertThat(res.getStatus()).as(path).isEqualTo(200);
            assertThat(res.getContentType()).startsWith("text/html");
            assertThat(res.getHeader("Cache-Control")).contains("no-store");
            String html = res.getContentAsString();
            assertThat(html).contains("<section lang=\"nb\">").contains("<section lang=\"en\">")
                    .contains("Personvern og samtykke").contains("Privacy and consent")
                    .contains("Støttet av").contains("exoreaction.com")
                    .doesNotContain("<script").doesNotContain("src=\"http").doesNotContain("href=\"http://");
            // Norwegian first by default, English first with ?lang=en.
            assertThat(html.indexOf("lang=\"nb\">")).isLessThan(html.indexOf("<section lang=\"en\">"));
        }
        String en = mvc.perform(get("/privacy").param("lang", "en")).andReturn().getResponse().getContentAsString();
        assertThat(en.indexOf("<section lang=\"en\">")).isLessThan(en.indexOf("<section lang=\"nb\">"));
        assertThat(en).startsWith("<!doctype html>").contains("<html lang=\"en\">");
    }

    @Test
    void textsAreEscapedAndShowWhatTheCodeKeeps() throws Exception {
        String html = mvc.perform(get("/privacy")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Administratorer ser navn og e-postadresse").contains("En vekt per sesong")
                .contains("Før du har svart").contains("lokal lagring");
        assertThat(html).contains("Administrators see the name and email address").contains("A weight per season")
                .contains("Before you have answered").contains("local storage");
    }
}
