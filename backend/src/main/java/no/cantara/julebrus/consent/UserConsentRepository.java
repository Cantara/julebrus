package no.cantara.julebrus.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {
    Optional<UserConsent> findByUserIdAndConsentVersion(UUID userId, String consentVersion);

    List<UserConsent> findByUserIdAndWithdrawnAtIsNull(UUID userId);

    List<UserConsent> findByWithdrawnAtIsNull();

    @org.springframework.data.jpa.repository.Query("select c.userId from UserConsent c where c.consentVersion = :version and c.withdrawnAt is null")
    List<UUID> findActiveUserIds(@org.springframework.data.repository.query.Param("version") String version);
}
