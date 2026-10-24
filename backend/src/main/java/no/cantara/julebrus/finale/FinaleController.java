package no.cantara.julebrus.finale;

import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FinaleController {

    private final FinaleService finale;
    private final CurrentUser currentUser;
    private final AuthProperties auth;

    public FinaleController(FinaleService finale, CurrentUser currentUser, AuthProperties auth) {
        this.finale = finale;
        this.currentUser = currentUser;
        this.auth = auth;
    }

    /** Countdown and totals before the reveal; podiums and awards after. Admins may preview. */
    @GetMapping("/api/finale")
    public FinaleService.FinaleView finale(@RequestParam(defaultValue = "false") boolean preview) {
        if (preview && !auth.isAdmin(currentUser.get().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");
        }
        return finale.view(preview);
    }
}
