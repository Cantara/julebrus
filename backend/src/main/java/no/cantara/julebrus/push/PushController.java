package no.cantara.julebrus.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.GeneralSecurityException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/push")
public class PushController {

    /** The public key the browser subscribes with; null while the feature is off. */
    public record Config(boolean enabled, String publicKey, LocalTime nudgeTime) {
    }

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }

    public record SubscribeRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {
    }

    public record EndpointRequest(@NotBlank String endpoint) {
    }

    /** The person's own browsers, by endpoint, so the page can tell whether this one is among them. */
    public record Mine(List<String> endpoints) {
    }

    private final PushService service;
    private final PushProperties props;
    private final CurrentUser currentUser;
    private final AuthProperties auth;

    public PushController(PushService service, PushProperties props, CurrentUser currentUser, AuthProperties auth) {
        this.service = service;
        this.props = props;
        this.currentUser = currentUser;
        this.auth = auth;
    }

    @GetMapping("/config")
    public Config config() {
        return new Config(props.enabled(), props.enabled() ? props.vapidPublicKey() : null, props.nudgeTime());
    }

    @GetMapping("/me")
    public Mine mine() {
        return new Mine(service.mine(currentUser.get().getId()).stream().map(PushSubscription::getEndpoint).toList());
    }

    @PostMapping("/subscriptions")
    public Mine subscribe(@Valid @RequestBody SubscribeRequest in, @RequestHeader(value = "User-Agent", required = false) String agent) {
        UUID me = currentUser.get().getId();
        service.subscribe(me, in.endpoint(), in.keys().p256dh(), in.keys().auth(), agent);
        return mine();
    }

    @DeleteMapping("/subscriptions")
    public Mine unsubscribe(@Valid @RequestBody EndpointRequest in) {
        service.unsubscribe(currentUser.get().getId(), in.endpoint());
        return mine();
    }

    /** Sends "Varsler er på" to the person's own browsers. */
    @PostMapping("/test")
    public Map<String, Integer> test() {
        return Map.of("sent", service.sendTest(currentUser.get().getId()));
    }

    /** Admins only: a fresh VAPID pair to put in the environment. Nothing is stored. */
    @GetMapping("/keygen")
    public Vapid.Keys keygen() throws GeneralSecurityException {
        if (!auth.isAdmin(currentUser.get().getEmail())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admins only");
        }
        return Vapid.generate();
    }
}
