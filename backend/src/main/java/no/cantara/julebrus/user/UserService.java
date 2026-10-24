package no.cantara.julebrus.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Finds or creates the user. Runs outside any caller transaction on purpose: several
     * first requests from one person can arrive together, one insert loses on the unique
     * email, and that loss must not poison a surrounding transaction. The loser re-reads
     * the winner's row.
     *
     * <p>WARNING: do not call this from a {@code @Transactional} method; the caller's transaction
     * would be suspended, and a lost race must not run inside one.</p>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public User upsert(String email, String displayName) {
        String name = (displayName == null || displayName.isBlank()) ? email : displayName;
        User existing = users.findByEmailIgnoreCase(email).orElse(null);
        if (existing == null) {
            try {
                return users.saveAndFlush(new User(email, name));
            } catch (DataIntegrityViolationException raced) {
                existing = users.findByEmailIgnoreCase(email).orElseThrow(() -> raced);
            }
        }
        if (!name.equals(existing.getDisplayName())) {
            existing.setDisplayName(name);
            existing = users.save(existing);
        }
        return existing;
    }
}
