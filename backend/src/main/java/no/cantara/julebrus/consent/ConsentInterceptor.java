package no.cantara.julebrus.consent;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Server-side enforcement: an API call by someone who has not accepted the current
 * consent version is answered 403 CONSENT_REQUIRED, whatever the frontend does.
 * Registered for /api/** in {@link ConsentWebConfig}, which lists the exceptions.
 */
@Component
public class ConsentInterceptor implements HandlerInterceptor {

    private final ConsentService consents;
    private final CurrentUser currentUser;

    public ConsentInterceptor(ConsentService consents, CurrentUser currentUser) {
        this.consents = consents;
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflights carry no credentials and never reach a controller method.
        if ("OPTIONS".equals(request.getMethod())) return true;
        // Throws 401 when nobody is logged in; the security chain normally answers that first.
        if (!consents.hasAccepted(currentUser.get().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_REQUIRED",
                    "Accept the current consent notice (" + ConsentService.CONSENT_VERSION + ") to use the app");
        }
        return true;
    }
}
