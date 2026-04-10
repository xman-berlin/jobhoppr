package at.jobhoppr.domain.person;

import at.jobhoppr.domain.bis.BerufSpezialisierungRepository;
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
public class PersonService {

    private final PersonRepository personRepository;
    private final BerufSpezialisierungRepository berufSpezialisierungRepository;
    private final KompetenzRepository kompetenzRepository;

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
        validiereReferenzen(req.berufSpezialisierungId(), req.kompetenzIds());
        Person p = new Person();
        return aktualisiereFelder(p, req.vorname(), req.nachname(), req.email(), req.berufSpezialisierungId());
    }

    public Person aktualisieren(UUID id, PersonCreateRequest req) {
        validiereReferenzen(req.berufSpezialisierungId(), req.kompetenzIds());
        Person p = findById(id);
        return aktualisiereFelder(p, req.vorname(), req.nachname(), req.email(), req.berufSpezialisierungId());
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
        p.getOrte().add(ort);
        personRepository.save(p);
        return ort;
    }

    public void ortEntfernen(UUID personId, UUID ortId) {
        Person p = findById(personId);
        p.getOrte().removeIf(o -> o.getId().equals(ortId));
        personRepository.save(p);
    }

    private Person aktualisiereFelder(Person p, String vorname, String nachname, String email, Integer berufSpezialisierungId) {
        p.setVorname(vorname);
        p.setNachname(nachname);
        p.setEmail(email);
        p.setBerufSpezialisierungId(berufSpezialisierungId);
        return personRepository.save(p);
    }

    private void validiereReferenzen(Integer berufSpezialisierungId, List<Integer> kompetenzIds) {
        if (berufSpezialisierungId != null && !berufSpezialisierungRepository.existsById(berufSpezialisierungId))
            throw new IllegalArgumentException("BerufSpezialisierung nicht gefunden: " + berufSpezialisierungId);
        if (kompetenzIds != null) {
            for (Integer kid : kompetenzIds) {
                if (!kompetenzRepository.existsById(kid))
                    throw new IllegalArgumentException("Kompetenz nicht gefunden: " + kid);
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
            Integer berufSpezialisierungId, List<Integer> kompetenzIds) {}

    public record OrtRequest(
            String ortRolle, String ortTyp, String bezeichnung,
            double lat, double lon, double umkreisKm) {}
}
