package at.jobhoppr.domain.person;

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
public class PersonService {

    private final PersonRepository personRepository;
    private final BerufSpezialisierungRepository berufSpezialisierungRepository;
    private final KompetenzRepository kompetenzRepository;
    private final InteressensgebietRepository interessensgebietRepository;
    private final VoraussetzungRepository voraussetzungRepository;

    @Transactional(readOnly = true)
    public Page<Person> findAll(Pageable pageable) {
        return personRepository.findAllByOrderByNachnameAscVornameAsc(pageable);
    }

    @Transactional(readOnly = true)
    public Person findById(UUID id) {
        return personRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Person nicht gefunden: " + id));
    }

    @Transactional(readOnly = true)
    public List<KompetenzEintrag> findKompetenzen(UUID personId) {
        Person p = personRepository.findByIdWithKompetenzen(personId)
                .orElseThrow(() -> new EntityNotFoundException("Person nicht gefunden: " + personId));
        return p.getKompetenzen().stream()
                .map(pk -> new KompetenzEintrag(pk.getId().getKompetenzId(), pk.getNiveau()))
                .toList();
    }

    public record KompetenzEintrag(Integer kompetenzId, String niveau) {}

    public Person erstellen(PersonCreateRequest req) {
        validiereReferenzen(req.berufSpezialisierungId(), req.kompetenzIds(), req.interessenIds(), req.voraussetzungIds());
        Person p = new Person();
        // Erst ohne Kompetenzen speichern, damit die UUID generiert wird
        p.setVorname(req.vorname());
        p.setNachname(req.nachname());
        p.setEmail(req.email());
        p.setBerufSpezialisierungId(req.berufSpezialisierungId());
        p.setSuchtLehrstelle(req.suchtLehrstelle() != null && req.suchtLehrstelle());
        if (req.interessenIds() != null) p.getInteressenIds().addAll(req.interessenIds());
        if (req.voraussetzungIds() != null) p.getVoraussetzungIds().addAll(req.voraussetzungIds());
        personRepository.save(p);
        // Jetzt Kompetenzen setzen (UUID ist bekannt)
        if (req.kompetenzIds() != null) {
            for (Integer kid : req.kompetenzIds()) {
                PersonKompetenz pk = new PersonKompetenz();
                pk.setId(new PersonKompetenz.PersonKompetenzId(p.getId(), kid));
                pk.setPerson(p);
                pk.setNiveau("GRUNDKENNTNISSE");
                p.getKompetenzen().add(pk);
            }
            personRepository.save(p);
        }
        return p;
    }

    public Person aktualisieren(UUID id, PersonCreateRequest req) {
        validiereReferenzen(req.berufSpezialisierungId(), req.kompetenzIds(), req.interessenIds(), req.voraussetzungIds());
        Person p = findById(id);
        return aktualisiereFelder(p, req);
    }

    public void loeschen(UUID id) {
        personRepository.deleteById(id);
    }

    public PersonOrt ortHinzufuegen(UUID personId, OrtRequest req) {
        Person p = findById(personId);
        PersonOrt ort = new PersonOrt();
        ort.setPerson(p);
        ort.setOrtRolle(req.ortRolle());
        ort.setOrtTyp(req.ortTyp());
        ort.setBezeichnung(req.bezeichnung());
        ort.setLat(req.lat());
        ort.setLon(req.lon());
        ort.setUmkreisKm(req.umkreisKm());
        ort.setGeoLocationId(req.geoLocationId());
        ort.setBundesweit(req.bundesweit() != null && req.bundesweit());
        p.getOrte().add(ort);
        personRepository.save(p);
        return ort;
    }

    public void ortEntfernen(UUID personId, UUID ortId) {
        Person p = findById(personId);
        p.getOrte().removeIf(o -> o.getId().equals(ortId));
        personRepository.save(p);
    }

    private Person aktualisiereFelder(Person p, PersonCreateRequest req) {
        p.setVorname(req.vorname());
        p.setNachname(req.nachname());
        p.setEmail(req.email());
        p.setBerufSpezialisierungId(req.berufSpezialisierungId());
        p.setSuchtLehrstelle(req.suchtLehrstelle() != null && req.suchtLehrstelle());
        // Interessen + Voraussetzungen ersetzen
        p.getInteressenIds().clear();
        if (req.interessenIds() != null) p.getInteressenIds().addAll(req.interessenIds());
        p.getVoraussetzungIds().clear();
        if (req.voraussetzungIds() != null) p.getVoraussetzungIds().addAll(req.voraussetzungIds());
        // Kompetenzen hinzufügen (nur neue — vorhandene bleiben erhalten)
        if (req.kompetenzIds() != null) {
            for (Integer kid : req.kompetenzIds()) {
                boolean exists = p.getKompetenzen().stream()
                        .anyMatch(pk -> pk.getId().getKompetenzId().equals(kid));
                if (!exists) {
                    PersonKompetenz pk = new PersonKompetenz();
                    pk.setId(new PersonKompetenz.PersonKompetenzId(p.getId(), kid));
                    pk.setPerson(p);
                    pk.setNiveau("GRUNDKENNTNISSE");
                    p.getKompetenzen().add(pk);
                }
            }
        }
        return personRepository.save(p);
    }

    private void validiereReferenzen(Integer berufSpezialisierungId, List<Integer> kompetenzIds,
                                     Set<Integer> interessenIds, Set<Integer> voraussetzungIds) {
        if (berufSpezialisierungId != null && !berufSpezialisierungRepository.existsById(berufSpezialisierungId))
            throw new IllegalArgumentException("BerufSpezialisierung nicht gefunden: " + berufSpezialisierungId);
        if (kompetenzIds != null) {
            for (Integer kid : kompetenzIds) {
                if (!kompetenzRepository.existsById(kid))
                    throw new IllegalArgumentException("Kompetenz nicht gefunden: " + kid);
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

    public PersonKompetenz kompetenzHinzufuegen(UUID personId, Integer kompetenzId, String niveau) {
        Person p = findById(personId);
        if (!kompetenzRepository.existsById(kompetenzId))
            throw new IllegalArgumentException("Kompetenz nicht gefunden: " + kompetenzId);
        PersonKompetenz pk = new PersonKompetenz();
        pk.setId(new PersonKompetenz.PersonKompetenzId(personId, kompetenzId));
        pk.setPerson(p);
        pk.setNiveau(niveau);
        p.getKompetenzen().add(pk);
        personRepository.save(p);
        return pk;
    }

    public void kompetenzEntfernen(UUID personId, Integer kompetenzId) {
        Person p = findById(personId);
        p.getKompetenzen().removeIf(pk -> pk.getId().getKompetenzId().equals(kompetenzId));
        personRepository.save(p);
    }

    public record PersonCreateRequest(
            String vorname, String nachname, String email,
            Integer berufSpezialisierungId, Boolean suchtLehrstelle,
            Set<Integer> interessenIds, Set<Integer> voraussetzungIds,
            List<Integer> kompetenzIds) {}

    public record OrtRequest(
            String ortRolle, String ortTyp, String bezeichnung,
            double lat, double lon, double umkreisKm,
            Integer geoLocationId, Boolean bundesweit) {}
}
