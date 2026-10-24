package no.cantara.julebrus;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test helper: every API call except /api/me and /api/consent needs accepted consent first. */
public final class ConsentSupport {

    private ConsentSupport() {
    }

    /** Accepts the current consent version as the given dev user; null means the default dev user. */
    public static void accept(MockMvc mvc, String email) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/consent/accept").contentType(APPLICATION_JSON)
                .content("{\"version\":\"%s\"}".formatted(no.cantara.julebrus.consent.ConsentService.CONSENT_VERSION));
        if (email != null) req.header("X-Dev-User", email);
        mvc.perform(req).andExpect(status().isOk());
    }
}
