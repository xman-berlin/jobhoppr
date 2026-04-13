package at.jobhoppr.domain.stelle;

import at.jobhoppr.domain.bis.BerufSpezialisierungRepository;
import at.jobhoppr.domain.bis.InteressensgebietRepository;
import at.jobhoppr.domain.bis.KompetenzRepository;
import at.jobhoppr.domain.bis.VoraussetzungRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StelleService {

    private final StelleRepository stelleRepository;
    private final BerufSpezialisierungRepository berufSpezialisierungRepository;
    private final KompetenzRepository kompetenzRepository;
    private final InteressensgebietRepository interessensgebietRepository;
    private final VoraussetzungRepository voraussetzungRepository;

    @Transactional(readOnly = true)
    public Page<Stelle> findAll(Pageable pageable) {
        return stelleRepository.findAllByOrderByErstelltAmDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Stelle> findAllByTyp(StelleTyp typ, Pageable pageable) {
        return stelleRepository.findAllByTypOrderByErstelltAmDesc(typ, pageable);
    }

    @Transactional(readOnly = true)
    public Stelle findById(UUID id) {
        return stelleRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Stelle nicht gefunden: " + id));
    }

    public Stelle erstellen(StelleRequest req) {
        validiereReferenzen(req.berufSpezialisierungId(), req.kompetenzEintraege(), req.interessenIds(), req.voraussetzungIds());
        Stelle s = new Stelle();
        // Erst ohne Kompetenzen speichern, damit die UUID generiert wird
        s.setTitel(req.titel());
        s.setUnternehmen(req.unternehmen());
        s.setBeschreibung(req.beschreibung());
        s.setOrtBezeichnung(req.ortBezeichnung());
        s.setOrtLat(req.ortLat());
        s.setOrtLon(req.ortLon());
        s.setBerufSpezialisierungId(req.berufSpezialisierungId());
        s.setGeoLocationId(req.geoLocationId());
        s.setTyp(req.typ() != null ? req.typ() : StelleTyp.STANDARD);
        if (req.interessenIds() != null) s.getInteressenIds().addAll(req.interessenIds());
        if (req.voraussetzungIds() != null) s.getVoraussetzungIds().addAll(req.voraussetzungIds());
        if (req.arbeitszeiten() != null) {
            for (ArbeitszeitEintrag ae : req.arbeitszeiten()) {
                s.getArbeitszeiten().add(new StelleArbeitszeit(s, ae.modell(), ae.pflicht()));
            }
        }
        stelleRepository.save(s);
        // Jetzt Kompetenzen setzen (UUID ist bekannt)
        if (req.kompetenzEintraege() != null) {
            for (KompetenzEintrag ke : req.kompetenzEintraege()) {
                StelleKompetenz sk = new StelleKompetenz();
                sk.setId(new StelleKompetenz.StelleKompetenzId(s.getId(), ke.kompetenzId()));
                sk.setStelle(s);
                sk.setPflicht(ke.pflicht());
                s.getKompetenzen().add(sk);
            }
            stelleRepository.save(s);
        }
        return s;
    }

    public Stelle aktualisieren(UUID id, StelleRequest req) {
        validiereReferenzen(req.berufSpezialisierungId(), req.kompetenzEintraege(), req.interessenIds(), req.voraussetzungIds());
        Stelle s = findById(id);
        // Kompetenzen nur ersetzen wenn explizit mitgeschickt;
        // bei null belassen (HTMX verwaltet sie separat via POST /kompetenzen)
        if (req.kompetenzEintraege() != null) {
            s.getKompetenzen().clear();
        }
        return aktualisiereFelder(s, req);
    }

    @Transactional(readOnly = true)
    public List<KompetenzEintrag> findKompetenzen(UUID stelleId) {
        Stelle s = stelleRepository.findByIdWithKompetenzen(stelleId)
                .orElseThrow(() -> new EntityNotFoundException("Stelle nicht gefunden: " + stelleId));
        return s.getKompetenzen().stream()
                .map(sk -> new KompetenzEintrag(sk.getId().getKompetenzId(), sk.getPflicht()))
                .toList();
    }

    public StelleKompetenz kompetenzHinzufuegen(UUID stelleId, Integer kompetenzId, boolean pflicht) {
        Stelle s = findById(stelleId);
        if (!kompetenzRepository.existsById(kompetenzId))
            throw new IllegalArgumentException("Kompetenz nicht gefunden: " + kompetenzId);
        StelleKompetenz sk = new StelleKompetenz();
        sk.setId(new StelleKompetenz.StelleKompetenzId(stelleId, kompetenzId));
        sk.setStelle(s);
        sk.setPflicht(pflicht);
        s.getKompetenzen().add(sk);
        stelleRepository.save(s);
        return sk;
    }

    public void kompetenzEntfernen(UUID stelleId, Integer kompetenzId) {
        Stelle s = findById(stelleId);
        s.getKompetenzen().removeIf(sk -> sk.getId().getKompetenzId().equals(kompetenzId));
        stelleRepository.save(s);
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
        s.setBerufSpezialisierungId(req.berufSpezialisierungId());
        s.setGeoLocationId(req.geoLocationId());
        s.setTyp(req.typ() != null ? req.typ() : StelleTyp.STANDARD);
        // Interessen + Voraussetzungen ersetzen
        s.getInteressenIds().clear();
        if (req.interessenIds() != null) s.getInteressenIds().addAll(req.interessenIds());
        s.getVoraussetzungIds().clear();
        if (req.voraussetzungIds() != null) s.getVoraussetzungIds().addAll(req.voraussetzungIds());
        if (req.kompetenzEintraege() != null) {
            for (KompetenzEintrag ke : req.kompetenzEintraege()) {
                StelleKompetenz sk = new StelleKompetenz();
                sk.setId(new StelleKompetenz.StelleKompetenzId(s.getId(), ke.kompetenzId()));
                sk.setStelle(s);
                sk.setPflicht(ke.pflicht());
                s.getKompetenzen().add(sk);
            }
        }
        // Arbeitszeiten ersetzen
        s.getArbeitszeiten().clear();
        if (req.arbeitszeiten() != null) {
            for (ArbeitszeitEintrag ae : req.arbeitszeiten()) {
                s.getArbeitszeiten().add(new StelleArbeitszeit(s, ae.modell(), ae.pflicht()));
            }
        }
        return stelleRepository.save(s);
    }

    private void validiereReferenzen(Integer berufSpezialisierungId, List<KompetenzEintrag> kompetenzen,
                                     Set<Integer> interessenIds, Set<Integer> voraussetzungIds) {
        if (berufSpezialisierungId != null && !berufSpezialisierungRepository.existsById(berufSpezialisierungId))
            throw new IllegalArgumentException("BerufSpezialisierung nicht gefunden: " + berufSpezialisierungId);
        if (kompetenzen != null) {
            for (KompetenzEintrag ke : kompetenzen) {
                if (!kompetenzRepository.existsById(ke.kompetenzId()))
                    throw new IllegalArgumentException("Kompetenz nicht gefunden: " + ke.kompetenzId());
            }
        }
        if (interessenIds != null) {
            for (Integer iid : interessenIds) {
                if (!interessensgebietRepository.existsById(iid))
                    throw new IllegalArgumentException("Interessensgebiet nicht gefunden: " + iid);
            }
        }
        if (voraussetzungIds != null) {
            for (Integer vid : voraussetzungIds) {
                if (!voraussetzungRepository.existsById(vid))
                    throw new IllegalArgumentException("Voraussetzung nicht gefunden: " + vid);
            }
        }
    }

    public record StelleRequest(
            String titel, String unternehmen, String beschreibung,
            String ortBezeichnung, double ortLat, double ortLon,
            Integer berufSpezialisierungId, StelleTyp typ,
            Set<Integer> interessenIds, Set<Integer> voraussetzungIds,
            List<KompetenzEintrag> kompetenzEintraege,
            Integer geoLocationId,
            List<ArbeitszeitEintrag> arbeitszeiten) {}

    public record KompetenzEintrag(Integer kompetenzId, boolean pflicht) {}
    public record ArbeitszeitEintrag(String modell, boolean pflicht) {}
}
