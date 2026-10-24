package no.cantara.julebrus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.photo.BeerPhoto;
import no.cantara.julebrus.photo.BeerPhotoRepository;
import no.cantara.julebrus.photo.PhotoService;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Label photos waiting for a yes or no. No image bytes here: the page loads each through the image endpoint. */
@RestController
@RequestMapping("/api/admin/photos")
public class AdminPhotosController {

    public record Decision(@NotNull PhotoService.Choice decision) {
    }

    private final CurrentUser currentUser;
    private final AuthProperties auth;
    private final PhotoService photos;
    private final ConsentService consents;

    public AdminPhotosController(CurrentUser currentUser, AuthProperties auth, PhotoService photos, ConsentService consents) {
        this.currentUser = currentUser;
        this.auth = auth;
        this.photos = photos;
        this.consents = consents;
    }

    private User admin() {
        User me = currentUser.get();
        if (!auth.isAdmin(me.getEmail())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admins only");
        return me;
    }

    /** Pictures by people with active consent only: someone who withdrew is not listed with name and email. */
    @GetMapping
    public List<BeerPhotoRepository.AdminRow> list(@RequestParam(defaultValue = "PENDING") BeerPhoto.Status status) {
        admin();
        var active = consents.activeUserIds();
        return photos.queue(status).stream().filter(r -> active.contains(r.userId())).toList();
    }

    @PostMapping("/{id}/decision")
    public BeerPhotoRepository.AdminRow decide(@PathVariable UUID id, @Valid @RequestBody Decision in) {
        return photos.decide(id, in.decision(), admin());
    }
}
