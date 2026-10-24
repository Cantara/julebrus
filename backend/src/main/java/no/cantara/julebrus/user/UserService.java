package no.cantara.julebrus.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public User upsert(String email, String displayName) {
        String name = (displayName == null || displayName.isBlank()) ? email : displayName;
        return users.findByEmailIgnoreCase(email)
                .map(u -> {
                    if (!name.equals(u.getDisplayName())) u.setDisplayName(name);
                    return u;
                })
                .orElseGet(() -> users.save(new User(email, name)));
    }
}
