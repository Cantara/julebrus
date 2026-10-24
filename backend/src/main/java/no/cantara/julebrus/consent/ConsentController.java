package no.cantara.julebrus.consent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/consent")
public class ConsentController {

    public record ConsentView(String version, boolean accepted, ConsentService.Texts texts) {
    }

    public record AcceptRequest(@NotBlank String version) {
    }

    private final ConsentService consents;
    private final CurrentUser currentUser;

    public ConsentController(ConsentService consents, CurrentUser currentUser) {
        this.consents = consents;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ConsentView get() {
        return view(currentUser.get().getId());
    }

    /** The version must be the current one, so nobody accepts text they were not shown. */
    @PostMapping("/accept")
    public ConsentView accept(@Valid @RequestBody AcceptRequest in) {
        if (!ConsentService.CONSENT_VERSION.equals(in.version())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONSENT_VERSION_MISMATCH",
                    "The consent text has changed; current version is " + ConsentService.CONSENT_VERSION);
        }
        UUID id = currentUser.get().getId();
        consents.accept(id);
        return view(id);
    }

    @PostMapping("/withdraw")
    public ConsentView withdraw() {
        UUID id = currentUser.get().getId();
        consents.withdraw(id);
        return view(id);
    }

    private ConsentView view(UUID userId) {
        return new ConsentView(ConsentService.CONSENT_VERSION, consents.hasAccepted(userId), consents.texts());
    }
}
