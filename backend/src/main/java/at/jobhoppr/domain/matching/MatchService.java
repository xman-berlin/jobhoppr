package at.jobhoppr.domain.matching;

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

    public List<MatchResult> matchenFuerStelle(UUID stelleId) {
        MatchModell modell = matchModellService.getAktives();
        return matchRepository.findTopPersonenForStelle(stelleId, modell);
    }

    public List<MatchResult> matchenFuerPerson(UUID personId) {
        MatchModell modell = matchModellService.getAktives();
        return matchRepository.findTopStellenForPerson(personId, modell);
    }
}
