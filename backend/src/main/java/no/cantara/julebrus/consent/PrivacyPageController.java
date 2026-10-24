package no.cantara.julebrus.consent;

import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.SponsorProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * The privacy policy as plain server-rendered HTML at /privacy, for crawlers and reviewers
 * that do not run JavaScript (Google's OAuth verification fetches the URL). Both languages
 * on one page, {@code ?lang=en} puts English first. No scripts, no external resources.
 * Public: permitted in {@code SecurityConfig}; it is not under /api, so the consent
 * interceptor never sees it. The Vue app links here with a full page load.
 */
@RestController
public class PrivacyPageController {

    private final ConsentService consents;
    private final SponsorProperties sponsor;
    private final ContactProperties contact;

    public PrivacyPageController(ConsentService consents, SponsorProperties sponsor, ContactProperties contact) {
        this.consents = consents;
        this.sponsor = sponsor;
        this.contact = contact;
    }

    @GetMapping(value = {"/privacy", "/privacy/"}, produces = "text/html;charset=UTF-8")
    public ResponseEntity<byte[]> page(@RequestParam(required = false) String lang) {
        boolean englishFirst = "en".equalsIgnoreCase(lang);
        String nb = section("nb", consents.texts().nb());
        String en = section("en", consents.texts().en());
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="%s">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Personvern / Privacy - Cantaras juleølsmaking</title>
                <style>
                body{font:16px/1.5 system-ui,sans-serif;max-width:40rem;margin:0 auto;padding:1rem;color:#241a1a;background:#faf6f0}
                h1{font-size:1.5rem}h2{font-size:1.1rem;margin:1.4rem 0 .4rem}section{margin-bottom:2rem}
                nav a{margin-right:1rem}footer{color:#7a6f6a;font-size:.9rem}
                </style>
                </head>
                <body>
                <h1>Personvern / Privacy</h1>
                <nav><a href="?lang=nb" hreflang="nb">Norsk</a><a href="?lang=en" hreflang="en">English</a></nav>
                """.formatted(englishFirst ? "en" : "nb"));
        html.append(englishFirst ? en + nb : nb + en);
        html.append("<footer><p>Version ").append(esc(ConsentService.CONSENT_VERSION)).append("</p>");
        if (sponsor.visible()) {
            html.append("<p>").append(englishFirst ? "Sponsored by " : "Støttet av ");
            String name = esc(sponsor.name().trim());
            if (sponsor.safeUrl() != null) {
                html.append("<a href=\"").append(esc(sponsor.safeUrl())).append("\">").append(name).append("</a>");
            } else {
                html.append(name);
            }
            html.append("</p>");
        }
        if (contact.present()) {
            String mail = esc(contact.email().trim());
            html.append("<p>Kontakt / Contact: <a href=\"mailto:").append(mail).append("\">").append(mail).append("</a></p>");
        }
        html.append("<p><a href=\"/\">Julebrus</a></p></footer>\n</body>\n</html>\n");
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                // Static page: nothing may load or run, nobody may frame it, nothing leaks through the Referer.
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
                .header("Referrer-Policy", "no-referrer")
                .header("Permissions-Policy", "")
                .body(html.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Escapes only the characters HTML treats specially, so Norwegian letters stay readable in the source. */
    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s, "UTF-8");
    }

    /** The consent texts are small Markdown ("## heading", "- item", paragraphs); everything is escaped. */
    static String section(String lang, String text) {
        StringBuilder out = new StringBuilder("<section lang=\"" + lang + "\">\n");
        boolean inList = false;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("- ")) {
                if (!inList) out.append("<ul>\n");
                inList = true;
                out.append("<li>").append(esc(line.substring(2))).append("</li>\n");
                continue;
            }
            if (inList) out.append("</ul>\n");
            inList = false;
            if (line.isEmpty()) continue;
            if (line.startsWith("## ")) out.append("<h2>").append(esc(line.substring(3))).append("</h2>\n");
            else out.append("<p>").append(esc(line)).append("</p>\n");
        }
        if (inList) out.append("</ul>\n");
        return out.append("</section>\n").toString();
    }
}
