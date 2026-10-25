package no.cantara.julebrus.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The Vue router owns every path that is not /api or a static file, so deep links
 * and PWA launches land on index.html. "/" itself is {@code landing.LandingController}: the
 * public landing page for visitors who are not signed in, index.html for everyone else.
 */
@Controller
public class SpaController {

    @GetMapping({"/{path:[^.]*}", "/{path:[^.]*}/{sub:[^.]*}", "/{path:[^.]*}/{sub:[^.]*}/{rest:[^.]*}"})
    public String index() {
        return "forward:/index.html";
    }

    /** An archive beer is addressed by its name, and names have dots in them ("St. Hallvards For-Jul"). */
    @GetMapping("/historikk/ol/{name}")
    public String archiveBeer() {
        return "forward:/index.html";
    }
}
