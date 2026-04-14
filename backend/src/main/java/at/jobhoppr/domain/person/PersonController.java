package at.jobhoppr.domain.person;

import at.jobhoppr.domain.bis.BerufSpezialisierungRepository;
import at.jobhoppr.domain.bis.InteressensgebietRepository;
import at.jobhoppr.domain.bis.KompetenzRepository;
import at.jobhoppr.domain.bis.VoraussetzungRepository;
import at.jobhoppr.domain.geo.GeoLocation;
import at.jobhoppr.domain.geo.GeoLocationRepository;
import at.jobhoppr.domain.geo.GeoRestController;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/personen")
@RequiredArgsConstructor
public class PersonController {

    private static final List<String> ARBEITSZEIT_MODELLE =
            List.of("VOLLZEIT", "TEILZEIT", "GERINGFUEGIG", "NACHT", "WOCHENENDE");

    private final PersonService personService;
    private final BerufSpezialisierungRepository berufSpezialisierungRepository;
    private final KompetenzRepository kompetenzRepository;
    private final InteressensgebietRepository interessensgebietRepository;
    private final VoraussetzungRepository voraussetzungRepository;
    private final GeoLocationRepository geoLocationRepository;
    private final GeoRestController geoRestController;

    @GetMapping
    public String liste(@RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 20);
        model.addAttribute("seite", personService.findAll(pageable));
        model.addAttribute("currentPage", page);
        return "personen/liste";
    }

    @GetMapping("/neu")
    public String neu(Model model) {
        model.addAttribute("person", new Person());
        model.addAttribute("isNeu", true);
        model.addAttribute("alleInteressen", interessensgebietRepository.findAll());
        model.addAttribute("alleVoraussetzungen", voraussetzungRepository.findAll());
        model.addAttribute("bundeslaender", geoLocationRepository.findByEbeneOrderByName("BUNDESLAND"));
        model.addAttribute("arbeitszeitAusschluesse", java.util.Set.of());
        return "personen/formular";
    }

    @GetMapping("/{id}")
    public String bearbeiten(@PathVariable UUID id, Model model) {
        Person person = personService.findById(id);
        model.addAttribute("person", person);
        model.addAttribute("isNeu", false);
        model.addAttribute("arbeitszeitAusschluesse", new java.util.HashSet<>(person.getArbeitszeitAusschluesse()));
        if (person.getBerufSpezialisierungId() != null) {
            berufSpezialisierungRepository.findByIdWithPfad(person.getBerufSpezialisierungId())
                    .ifPresent(b -> model.addAttribute("berufName", b.getName()));
        }
        // Kompetenznamen als Map<kompetenzId, name> für das Template
        List<PersonService.KompetenzEintrag> kompetenzen = personService.findKompetenzen(id);
        model.addAttribute("personKompetenzen", kompetenzen);
        if (!kompetenzen.isEmpty()) {
            List<Integer> kompetenzIds = kompetenzen.stream()
                    .map(PersonService.KompetenzEintrag::kompetenzId).toList();
            Map<Integer, String> namen = kompetenzRepository.findAllById(kompetenzIds).stream()
                    .collect(Collectors.toMap(k -> k.getId(), k -> k.getName()));
            model.addAttribute("kompetenzNamen", namen);
        }
        model.addAttribute("alleInteressen", interessensgebietRepository.findAll());
        model.addAttribute("alleVoraussetzungen", voraussetzungRepository.findAll());
        model.addAttribute("bundeslaender", geoLocationRepository.findByEbeneOrderByName("BUNDESLAND"));
        model.addAttribute("arbeitszeitModelle", ARBEITSZEIT_MODELLE);
        return "personen/formular";
    }

    @PostMapping
    public String erstellen(
            @RequestParam String vorname,
            @RequestParam String nachname,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer berufSpezialisierungId,
            @RequestParam(required = false) Boolean suchtLehrstelle,
            @RequestParam(required = false) Set<Integer> interessenIds,
            @RequestParam(required = false) Set<Integer> voraussetzungIds,
            @RequestParam(required = false) List<Integer> kompetenzIds,
            @RequestParam(required = false) Set<String> arbeitszeitAusschluesse) {

        Person p = personService.erstellen(new PersonService.PersonCreateRequest(
                vorname, nachname, email, berufSpezialisierungId,
                suchtLehrstelle, interessenIds, voraussetzungIds, kompetenzIds,
                arbeitszeitAusschluesse));
        return "redirect:/personen/" + p.getId();
    }

    @PostMapping("/{id}")
    public String aktualisieren(
            @PathVariable UUID id,
            @RequestParam String vorname,
            @RequestParam String nachname,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer berufSpezialisierungId,
            @RequestParam(required = false) Boolean suchtLehrstelle,
            @RequestParam(required = false) Set<Integer> interessenIds,
            @RequestParam(required = false) Set<Integer> voraussetzungIds,
            @RequestParam(required = false) List<Integer> kompetenzIds,
            @RequestParam(required = false) Set<String> arbeitszeitAusschluesse) {

        personService.aktualisieren(id, new PersonService.PersonCreateRequest(
                vorname, nachname, email, berufSpezialisierungId,
                suchtLehrstelle, interessenIds, voraussetzungIds, kompetenzIds,
                arbeitszeitAusschluesse));
        return "redirect:/personen/" + id;
    }

    @DeleteMapping("/{id}")
    @HxRequest
    public ResponseEntity<String> loeschen(@PathVariable UUID id) {
        personService.loeschen(id);
        return ResponseEntity.ok()
                .header("HX-Trigger", "{\"showToast\":\"Person gelöscht\"}")
                .header("HX-Redirect", "/personen")
                .body("");
    }

    // ── HTMX fragments for PersonOrt ──────────────────────────────────────────

    @PostMapping("/{id}/orte")
    @HxRequest
    public String ortHinzufuegen(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "WOHNORT") String ortRolle,
            @RequestParam(defaultValue = "GENAU") String ortTyp,
            @RequestParam(defaultValue = "") String bezeichnung,
            @RequestParam(defaultValue = "48.2082") double lat,
            @RequestParam(defaultValue = "16.3738") double lon,
            @RequestParam(defaultValue = "30") double umkreisKm,
            @RequestParam(required = false) Integer geoLocationId,
            @RequestParam(required = false) Boolean bundesweit,
            Model model) {

        PersonOrt ort = personService.ortHinzufuegen(id,
                new PersonService.OrtRequest(ortRolle, ortTyp, bezeichnung, lat, lon, umkreisKm,
                        geoLocationId, bundesweit));
        model.addAttribute("ort", ort);
        model.addAttribute("personId", id);
        return "personen/ort-fragment :: ort-eintrag";
    }

    @DeleteMapping("/{personId}/orte/{ortId}")
    @HxRequest
    public ResponseEntity<String> ortEntfernen(
            @PathVariable UUID personId, @PathVariable UUID ortId) {
        personService.ortEntfernen(personId, ortId);
        return ResponseEntity.ok().body("");
    }

    /** HTMX: returns Bezirk <select> for the given Bundesland parent. */
    @GetMapping("/orte/bezirke")
    @HxRequest
    public String bezirkeDropdown(@RequestParam Integer parentId, Model model) {
        model.addAttribute("bezirke", geoLocationRepository.findByParentIdOrderByName(parentId));
        return "personen/bezirk-fragment :: bezirk-select";
    }

    /** HTMX: returns Orte <select> for the given Bezirk parent. */
    @GetMapping("/orte/orte")
    @HxRequest
    public String orteDropdown(@RequestParam Integer parentId, Model model) {
        model.addAttribute("orte", geoLocationRepository.findByParentIdOrderByName(parentId));
        return "personen/ort-dropdown-fragment :: ort-select";
    }

    /** HTMX: returns search results for autocomplete */
    @GetMapping("/orte/suche")
    @HxRequest
    public String ortSuche(@RequestParam String q, Model model) {
        model.addAttribute("sucheErgebnisse", geoRestController.suche(q));
        return "personen/suche-ergebnis-fragment :: suchergebnisse";
    }

    // ── HTMX fragments for PersonKompetenz ───────────────────────────────────

    @PostMapping("/{id}/kompetenzen")
    @HxRequest
    public String kompetenzHinzufuegen(
            @PathVariable UUID id,
            @RequestParam Integer kompetenzId,
            @RequestParam(defaultValue = "GRUNDKENNTNISSE") String niveau,
            Model model) {

        PersonKompetenz pk = personService.kompetenzHinzufuegen(id, kompetenzId, niveau);
        model.addAttribute("pk", pk);
        model.addAttribute("personId", id);
        kompetenzRepository.findById(kompetenzId)
                .ifPresent(k -> model.addAttribute("kompetenzName", k.getName()));
        return "personen/kompetenz-fragment :: kompetenz-eintrag";
    }

    @DeleteMapping("/{personId}/kompetenzen/{kompetenzId}")
    @HxRequest
    public ResponseEntity<String> kompetenzEntfernen(
            @PathVariable UUID personId, @PathVariable Integer kompetenzId) {
        personService.kompetenzEntfernen(personId, kompetenzId);
        return ResponseEntity.ok().body("");
    }
}
