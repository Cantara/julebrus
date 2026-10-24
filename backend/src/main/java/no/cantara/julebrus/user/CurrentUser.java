package no.cantara.julebrus.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Resolves the logged-in {@link User}. Supports Google OIDC in production and the
 * {@link DevPrincipal} installed by the dev profile.
 */
@Component
public class CurrentUser {

    private final UserService userService;

    public CurrentUser(UserService userService) {
        this.userService = userService;
    }

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            return userService.upsert(oidc.getEmail(), oidc.getFullName());
        }
        if (principal instanceof DevPrincipal dev) {
            return userService.upsert(dev.email(), dev.name());
        }
        throw new ResponseStatusException(UNAUTHORIZED);
    }

    public record DevPrincipal(String email, String name) {
    }
}
