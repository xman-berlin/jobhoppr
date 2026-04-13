package at.jobhoppr.domain.person;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    @Query("SELECT p FROM Person p LEFT JOIN FETCH p.orte LEFT JOIN FETCH p.arbeitszeitAusschluesse WHERE p.id = :id")
    Optional<Person> findByIdWithDetails(UUID id);

    @Query("SELECT p FROM Person p LEFT JOIN FETCH p.kompetenzen WHERE p.id = :id")
    Optional<Person> findByIdWithKompetenzen(UUID id);

    Page<Person> findAllByOrderByNachnameAscVornameAsc(Pageable pageable);
}
