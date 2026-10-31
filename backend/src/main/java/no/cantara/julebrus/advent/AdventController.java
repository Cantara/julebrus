package no.cantara.julebrus.advent;

import no.cantara.julebrus.user.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** The advent calendar game: the overview, one door, and opening it with an answer. */
@RestController
@RequestMapping("/api/advent")
public class AdventController {

    public record OpenRequest(Integer answer) {
    }

    private final AdventService advent;
    private final CurrentUser currentUser;

    public AdventController(AdventService advent, CurrentUser currentUser) {
        this.advent = advent;
        this.currentUser = currentUser;
    }

    @GetMapping
    public AdventService.Overview overview() {
        return advent.overview(currentUser.get());
    }

    @GetMapping("/doors/{day}")
    public AdventService.DoorView door(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return advent.door(currentUser.get(), day);
    }

    @PostMapping("/doors/{day}/open")
    public AdventService.DoorView open(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                       @RequestBody(required = false) OpenRequest in) {
        return advent.open(currentUser.get(), day, in == null ? null : in.answer());
    }
}
