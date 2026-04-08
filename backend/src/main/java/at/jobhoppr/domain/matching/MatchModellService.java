package at.jobhoppr.domain.matching;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchModellService {

    private final MatchModellRepository matchModellRepository;

    @Transactional(readOnly = true)
    public MatchModell getAktives() {
        return matchModellRepository.findByAktivTrue()
                .orElseThrow(() -> new EntityNotFoundException("Kein aktives Match-Modell gefunden"));
    }

    public MatchModell aktualisieren(MatchModellRequest req) {
        MatchModell m = getAktives();
        m.setGeoAktiv(req.geoAktiv());
        m.setGewichtKompetenz(req.gewichtKompetenz());
        m.setGewichtBeruf(req.gewichtBeruf());
        m.setScoreSchwellenwert(req.scoreSchwellenwert());
        m.setGewichtLehrberuf(req.gewichtLehrberuf());
        m.setGewichtInteressen(req.gewichtInteressen());
        m.setGewichtVoraussetzungen(req.gewichtVoraussetzungen());
        return matchModellRepository.save(m);
    }

    public record MatchModellRequest(
            boolean geoAktiv,
            double gewichtKompetenz,
            double gewichtBeruf,
            double scoreSchwellenwert,
            double gewichtLehrberuf,
            double gewichtInteressen,
            double gewichtVoraussetzungen) {}
}
