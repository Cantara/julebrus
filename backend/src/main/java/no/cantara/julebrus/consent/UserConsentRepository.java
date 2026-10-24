package no.cantara.julebrus.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {
    Optional<UserConsent> findByUserIdAndConsentVersion(UUID userId, String consentVersion);

    List<UserConsent> findByUserIdAndWithdrawnAtIsNull(UUID userId);
}
