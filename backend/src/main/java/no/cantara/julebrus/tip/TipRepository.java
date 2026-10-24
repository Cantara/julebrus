package no.cantara.julebrus.tip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TipRepository extends JpaRepository<Tip, UUID> {
}
