package no.cantara.julebrus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.vinmonopolet.MatchService;
import no.cantara.julebrus.vinmonopolet.VmpCatalogue;
import no.cantara.julebrus.vinmonopolet.VmpImportService;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Review of the machine matches between tasting votes and Vinmonopolet beers. Admins only. */
@RestController
@RequestMapping("/api/admin")
public class AdminMatchesController {

    public record DecisionRequest(@NotNull MatchService.Action action, @NotNull Long version, String articleNo, String productName) {
    }

    public enum BulkAction { CONFIRM }

    public record BulkRequest(@NotNull BulkAction action, @NotNull Integer year, @NotNull String status, Boolean dryRun,
                              Integer expectedCount) {
    }

    private static final int SEARCH_LIMIT = 20;
    private static final int SEARCH_MAX_QUERY = 100;

    private final CurrentUser currentUser;
    private final AuthProperties auth;
    private final MatchService matches;
    private final VmpCatalogue catalogue;
    private final VmpImportService importer;

    public AdminMatchesController(CurrentUser currentUser, AuthProperties auth, MatchService matches, VmpCatalogue catalogue,
                                  VmpImportService importer) {
        this.currentUser = currentUser;
        this.auth = auth;
        this.matches = matches;
        this.catalogue = catalogue;
        this.importer = importer;
    }

    private User admin() {
        User me = currentUser.get();
        if (!auth.isAdmin(me.getEmail())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admins only");
        return me;
    }

    @GetMapping("/matches")
    public MatchService.MatchPage list(@RequestParam(required = false) String status, @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Boolean decided, @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        admin();
        return matches.list(status, year, decided, page, size);
    }

    @PostMapping("/matches/{id}/decision")
    public MatchService.MatchView decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest in) {
        User me = admin();
        return matches.decide(id, in.action(), in.version(), in.articleNo(), in.productName(), me.getId());
    }

    @PostMapping("/matches/bulk")
    public MatchService.BulkResult bulk(@Valid @RequestBody BulkRequest in) {
        User me = admin();
        return matches.bulkConfirm(in.year(), in.status(), in.dryRun() == null || in.dryRun(), in.expectedCount(), me.getId());
    }

    @GetMapping(value = "/matches/overrides.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> overrides() {
        admin();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vote_match_overrides.csv\"")
                .body(matches.overridesCsv().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/products/search")
    public List<VmpCatalogue.Hit> search(@RequestParam(defaultValue = "") String q) {
        admin();
        String query = q.length() > SEARCH_MAX_QUERY ? q.substring(0, SEARCH_MAX_QUERY) : q;
        return catalogue.search(query, SEARCH_LIMIT);
    }

    /** Re-runs the startup import: adds what the files have and the tables lack; decisions are never touched. */
    @PostMapping("/vinmonopolet/import")
    public VmpImportService.Result runImport() throws IOException {
        admin();
        return importer.importClasspath();
    }
}
