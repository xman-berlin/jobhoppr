package at.jobhoppr.domain.matching;

import at.jobhoppr.domain.person.Person;
import at.jobhoppr.domain.person.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchModellService matchModellService;
    private final PersonService personService;

    /**
     * Rankt Stellen für eine Person.
     * KO-Vorbedingung: Person muss vermittlungsbereit sein und noch Bewegungen übrig haben.
     */
    public List<MatchResult> matchenFuerPerson(UUID personId, SortierParameter sort) {
        Person person = personService.findById(personId);
        if (!Boolean.TRUE.equals(person.getVermittlungspost())) return List.of();
        if (person.getMaxBewegungen() != null && person.getMaxBewegungen() == 0) return List.of();
        MatchModell modell = matchModellService.getAktives();
        return matchRepository.findTopStellenForPerson(personId, modell, sort);
    }

    /** Rankt Personen für eine Stelle. */
    public List<MatchResult> matchenFuerStelle(UUID stelleId, SortierParameter sort) {
        MatchModell modell = matchModellService.getAktives();
        return matchRepository.findTopPersonenForStelle(stelleId, modell, sort);
    }

    // ── Convenience-Overloads mit Default-Sortierung ────────────────────────

    public List<MatchResult> matchenFuerPerson(UUID personId) {
        return matchenFuerPerson(personId, SortierParameter.DEFAULT);
    }

    public List<MatchResult> matchenFuerStelle(UUID stelleId) {
        return matchenFuerStelle(stelleId, SortierParameter.DEFAULT);
    }
}

