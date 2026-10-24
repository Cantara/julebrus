package no.cantara.julebrus.landing;

import jakarta.servlet.http.HttpServletRequest;
import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.config.SponsorProperties;
import no.cantara.julebrus.push.PushProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "/" for a visitor who is not signed in is the public landing page; a signed-in person goes
 * straight into the app (index.html, as before). {@code /velkommen} always shows the landing page,
 * for signed-in people who want to see it, for the dev profile (where everyone is signed in) and
 * for the app, which sends a logged-out visitor on "/" there instead of straight to Google.
 * {@code /welcome} and {@code /landing} are the same page under English-friendly names; the
 * language is still chosen by {@code ?lang=en}, as everywhere else.
 *
 * The page is rendered from {@link HistoryStats} and configuration only: it reads nothing from the
 * database and nothing about the visitor. Both languages are rendered once at startup.
 */
@Controller
public class LandingController {

    /** Nothing may load or run except the inline style sheet and the app's own icon; nothing may frame it. */
    public static final String CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; base-uri 'none'; "
            + "form-action 'none'; frame-ancestors 'none'";

    /** The paths that always show the page; each is rendered with itself as the language switch target. */
    static final List<String> PAGE_PATHS = List.of("/velkommen", "/welcome", "/landing");

    private final byte[] nbRoot, enRoot;
    /** path -> {nb, en} */
    private final Map<String, byte[][]> pages = new HashMap<>();

    public LandingController(SeasonProperties season, SponsorProperties sponsor, ContactProperties contact, PushProperties push) {
        HistoryStats stats = HistoryStats.load();
        // Only the nudge time reaches the page, and only when push is switched on; never the keys.
        LocalTime nudge = push.enabled() ? push.nudgeTime() : null;
        this.nbRoot = LandingPage.render(stats, season, sponsor, contact, nudge, false, "/").getBytes(StandardCharsets.UTF_8);
        this.enRoot = LandingPage.render(stats, season, sponsor, contact, nudge, true, "/").getBytes(StandardCharsets.UTF_8);
        for (String path : PAGE_PATHS) {
            pages.put(path, new byte[][]{
                    LandingPage.render(stats, season, sponsor, contact, nudge, false, path).getBytes(StandardCharsets.UTF_8),
                    LandingPage.render(stats, season, sponsor, contact, nudge, true, path).getBytes(StandardCharsets.UTF_8)});
        }
    }

    /**
     * Returns a forward (signed in) or the page (not signed in). Spring picks the return value
     * handler by the runtime type, so both work from one mapping. {@code principal} is null for an
     * anonymous visitor: Spring Security's request wrapper hides the anonymous token.
     */
    @GetMapping("/")
    public Object root(Principal principal, @RequestParam(required = false) String lang) {
        if (principal != null) return new ModelAndView("forward:/index.html");
        return page("en".equalsIgnoreCase(lang) ? enRoot : nbRoot);
    }

    @GetMapping({"/velkommen", "/velkommen/", "/welcome", "/welcome/", "/landing", "/landing/"})
    public ResponseEntity<byte[]> welcome(HttpServletRequest request, @RequestParam(required = false) String lang) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        byte[][] both = pages.getOrDefault(path, pages.get("/velkommen"));
        return page("en".equalsIgnoreCase(lang) ? both[1] : both[0]);
    }

    private static ResponseEntity<byte[]> page(byte[] body) {
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                // "/" answers differently for a signed-in person, so no shared cache may keep it.
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, "Cookie")
                .header("Content-Security-Policy", CSP)
                .header("Referrer-Policy", "no-referrer")
                .header("Permissions-Policy", "")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
