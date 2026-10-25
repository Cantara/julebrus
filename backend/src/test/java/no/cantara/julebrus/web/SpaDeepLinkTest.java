package no.cantara.julebrus.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

/** A refreshed page or a shared link must reach the Vue router, however deep the route is and whatever the beer's name looks like. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SpaDeepLinkTest {

    @Autowired
    MockMvc mvc;

    @Test
    void routesOfOneTwoAndThreeSegmentsReachIndexHtml() throws Exception {
        for (String path : new String[]{"/historikk", "/lag/cantara", "/beers/0b9d1c5e-1111-2222-3333-444455556666/rate"}) {
            mvc.perform(get(path)).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void anArchiveBeerOpensByNameEvenWithDotsAndSpaces() throws Exception {
        for (String name : new String[]{"Ringnes", "St.%20Hallvards%20For-Jul", "Aass%20Jule%C3%B8l%202.0", "%C3%86gir%20Ylir"}) {
            // a ready-made URI: a String would be encoded a second time (%20 -> %2520), which the firewall refuses
            mvc.perform(get(java.net.URI.create("/historikk/ol/" + name))).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void aMissingStaticFileIsStillA404() throws Exception {
        mvc.perform(get("/assets/does-not-exist.js")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
}
