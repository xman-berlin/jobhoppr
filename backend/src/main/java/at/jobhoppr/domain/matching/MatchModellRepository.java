package at.jobhoppr.domain.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchModellRepository extends JpaRepository<MatchModell, UUID> {
    Optional<MatchModell> findByAktivTrue();
}
