package at.jobhoppr.domain.stelle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface StelleRepository extends JpaRepository<Stelle, UUID> {

    @Query("SELECT s FROM Stelle s LEFT JOIN FETCH s.kompetenzen WHERE s.id = :id")
    Optional<Stelle> findByIdWithDetails(UUID id);

    Page<Stelle> findAllByOrderByErstelltAmDesc(Pageable pageable);
}
