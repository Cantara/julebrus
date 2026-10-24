package no.cantara.julebrus.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The Vue router owns every path that is not /api or a static file, so deep links
 * and PWA launches land on index.html.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/{path:[^.]*}", "/{path:[^.]*}/{sub:[^.]*}"})
    public String index() {
        return "forward:/index.html";
    }
}
