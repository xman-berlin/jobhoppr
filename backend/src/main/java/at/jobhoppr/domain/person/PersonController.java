package at.jobhoppr.domain.person;

import at.jobhoppr.domain.bis.BerufRepository;
import at.jobhoppr.domain.bis.KompetenzRepository;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/personen")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;
    private final BerufRepository berufRepository;
    private final KompetenzRepository kompetenzRepository;

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
        return "personen/formular";
    }

    @GetMapping("/{id}")
    public String bearbeiten(@PathVariable UUID id, Model model) {
        model.addAttribute("person", personService.findById(id));
        model.addAttribute("isNeu", false);
        return "personen/formular";
    }

    @PostMapping
    public String erstellen(
            @RequestParam String vorname,
            @RequestParam String nachname,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer berufId,
            @RequestParam(required = false) List<Integer> kompetenzIds) {

        Person p = personService.erstellen(new PersonService.PersonCreateRequest(
                vorname, nachname, email, berufId, kompetenzIds));
        return "redirect:/personen/" + p.getId();
    }

    @PostMapping("/{id}")
    public String aktualisieren(
            @PathVariable UUID id,
            @RequestParam String vorname,
            @RequestParam String nachname,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer berufId,
            @RequestParam(required = false) List<Integer> kompetenzIds) {

        personService.aktualisieren(id, new PersonService.PersonCreateRequest(
                vorname, nachname, email, berufId, kompetenzIds));
        return "redirect:/personen/" + id;
    }

    @DeleteMapping("/{id}")
    @HxRequest
    public ResponseEntity<String> loeschen(@PathVariable UUID id) {
        personService.loeschen(id);
        return ResponseEntity.ok()
                .header("HX-Trigger", "{\"showToast\":\"Person gelöscht\"}")
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
            Model model) {

        PersonOrt ort = personService.ortHinzufuegen(id,
                new PersonService.OrtRequest(ortRolle, ortTyp, bezeichnung, lat, lon, umkreisKm));
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
}
