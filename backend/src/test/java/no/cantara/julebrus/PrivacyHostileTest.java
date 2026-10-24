package no.cantara.julebrus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Configuration values are escaped on the server-rendered page, whatever they contain. */
@SpringBootTest(properties = {
        "julebrus.sponsor.name=<img src=x onerror=1>\"&",
        "julebrus.sponsor.url=https://a.no/\"onmouseover=\"x",
        "julebrus.contact.email=a\"><script>@b.no"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PrivacyHostileTest {

    @Autowired
    MockMvc mvc;

    @Test
    void hostileSponsorAndContactValuesAreEscaped() throws Exception {
        for (String lang : new String[]{"nb", "en"}) {
            String html = mvc.perform(get("/privacy").param("lang", lang)).andReturn().getResponse().getContentAsString();
            // No element came out of the values.
            assertThat(html).doesNotContain("<img").doesNotContain("<script").doesNotContain("onerror=1>")
                    .doesNotContain("\"onmouseover=\"");
            assertThat(html).contains("&lt;img src=x onerror=1&gt;&quot;&amp;")
                    .contains("https://a.no/&quot;onmouseover=&quot;x")
                    .contains("a&quot;&gt;&lt;script&gt;@b.no");
            // The consent text itself (from the same properties) is escaped too.
            assertThat(html).doesNotContain("<img src=x");
        }
    }
}
