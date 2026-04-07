package at.jobhoppr.domain.stelle;

import at.jobhoppr.domain.bis.BerufRepository;
import at.jobhoppr.domain.bis.KompetenzRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StelleService {

    private final StelleRepository stelleRepository;
    private final BerufRepository berufRepository;
    private final KompetenzRepository kompetenzRepository;

    @Transactional(readOnly = true)
    public Page<Stelle> findAll(Pageable pageable) {
        return stelleRepository.findAllByOrderByErstelltAmDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Stelle findById(UUID id) {
        return stelleRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Stelle nicht gefunden: " + id));
    }

    public Stelle erstellen(StelleRequest req) {
        validiereReferenzen(req.berufId(), req.kompetenzEintraege());
        Stelle s = new Stelle();
        return aktualisiereFelder(s, req);
    }

    public Stelle aktualisieren(UUID id, StelleRequest req) {
        validiereReferenzen(req.berufId(), req.kompetenzEintraege());
        Stelle s = findById(id);
        s.getKompetenzen().clear();
        return aktualisiereFelder(s, req);
    }

    public void loeschen(UUID id) {
        stelleRepository.deleteById(id);
    }

    private Stelle aktualisiereFelder(Stelle s, StelleRequest req) {
        s.setTitel(req.titel());
        s.setUnternehmen(req.unternehmen());
        s.setBeschreibung(req.beschreibung());
        s.setOrtBezeichnung(req.ortBezeichnung());
        s.setOrtLat(req.ortLat());
        s.setOrtLon(req.ortLon());
        s.setBerufId(req.berufId());
        if (req.kompetenzEintraege() != null) {
            for (KompetenzEintrag ke : req.kompetenzEintraege()) {
                StelleKompetenz sk = new StelleKompetenz();
                sk.setId(new StelleKompetenz.StelleKompetenzId(s.getId(), ke.kompetenzId()));
                sk.setStelle(s);
                sk.setPflicht(ke.pflicht());
                s.getKompetenzen().add(sk);
            }
        }
        return stelleRepository.save(s);
    }

    private void validiereReferenzen(Integer berufId, List<KompetenzEintrag> kompetenzen) {
        if (berufId != null && !berufRepository.existsById(berufId))
            throw new IllegalArgumentException("Beruf nicht gefunden: " + berufId);
        if (kompetenzen != null) {
            for (KompetenzEintrag ke : kompetenzen) {
                if (!kompetenzRepository.existsById(ke.kompetenzId()))
                    throw new IllegalArgumentException("Kompetenz nicht gefunden: " + ke.kompetenzId());
            }
        }
    }

    public record StelleRequest(
            String titel, String unternehmen, String beschreibung,
            String ortBezeichnung, double ortLat, double ortLon,
            Integer berufId, List<KompetenzEintrag> kompetenzEintraege) {}

    public record KompetenzEintrag(Integer kompetenzId, boolean pflicht) {}
}
