package at.jobhoppr.domain.stelle;

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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stellen")
@RequiredArgsConstructor
public class StelleController {

    private final StelleService stelleService;
    private final BerufRepository berufRepository;
    private final KompetenzRepository kompetenzRepository;

    @GetMapping
    public String liste(@RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 20);
        model.addAttribute("seite", stelleService.findAll(pageable));
        model.addAttribute("currentPage", page);
        return "stellen/liste";
    }

    @GetMapping("/neu")
    public String neu(Model model) {
        model.addAttribute("stelle", new Stelle());
        model.addAttribute("isNeu", true);
        return "stellen/formular";
    }

    @GetMapping("/{id}")
    public String bearbeiten(@PathVariable UUID id, Model model) {
        Stelle stelle = stelleService.findById(id);
        model.addAttribute("stelle", stelle);
        model.addAttribute("isNeu", false);
        if (stelle.getBerufId() != null) {
            berufRepository.findById(stelle.getBerufId())
                    .ifPresent(b -> model.addAttribute("berufName", b.getName()));
        }
        List<StelleService.KompetenzEintrag> kompetenzen = stelleService.findKompetenzen(id);
        model.addAttribute("stelleKompetenzen", kompetenzen);
        if (!kompetenzen.isEmpty()) {
            List<Integer> ids = kompetenzen.stream()
                    .map(StelleService.KompetenzEintrag::kompetenzId).toList();
            Map<Integer, String> namen = kompetenzRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(k -> k.getId(), k -> k.getName()));
            model.addAttribute("kompetenzNamen", namen);
        }
        return "stellen/formular";
    }

    @PostMapping
    public String erstellen(
            @RequestParam String titel,
            @RequestParam(required = false) String unternehmen,
            @RequestParam(required = false) String beschreibung,
            @RequestParam String ortBezeichnung,
            @RequestParam double ortLat,
            @RequestParam double ortLon,
            @RequestParam(required = false) Integer berufId,
            @RequestParam(required = false) List<Integer> kompetenzIds,
            @RequestParam(required = false) List<Boolean> pflichtFlags) {

        Stelle s = stelleService.erstellen(buildRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufId, kompetenzIds, pflichtFlags));
        return "redirect:/stellen/" + s.getId();
    }

    @PostMapping("/{id}")
    public String aktualisieren(
            @PathVariable UUID id,
            @RequestParam String titel,
            @RequestParam(required = false) String unternehmen,
            @RequestParam(required = false) String beschreibung,
            @RequestParam String ortBezeichnung,
            @RequestParam double ortLat,
            @RequestParam double ortLon,
            @RequestParam(required = false) Integer berufId,
            @RequestParam(required = false) List<Integer> kompetenzIds,
            @RequestParam(required = false) List<Boolean> pflichtFlags) {

        stelleService.aktualisieren(id, buildRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufId, kompetenzIds, pflichtFlags));
        return "redirect:/stellen/" + id;
    }

    @DeleteMapping("/{id}")
    @HxRequest
    public ResponseEntity<String> loeschen(@PathVariable UUID id) {
        stelleService.loeschen(id);
        return ResponseEntity.ok()
                .header("HX-Trigger", "{\"showToast\":\"Stelle gelöscht\"}")
                .body("");
    }

    // ── HTMX fragments for StelleKompetenz ───────────────────────────────────

    @PostMapping("/{id}/kompetenzen")
    @HxRequest
    public String kompetenzHinzufuegen(
            @PathVariable UUID id,
            @RequestParam Integer kompetenzId,
            @RequestParam(defaultValue = "true") boolean pflicht,
            Model model) {

        StelleKompetenz sk = stelleService.kompetenzHinzufuegen(id, kompetenzId, pflicht);
        model.addAttribute("sk", sk);
        model.addAttribute("stelleId", id);
        kompetenzRepository.findById(kompetenzId)
                .ifPresent(k -> model.addAttribute("kompetenzName", k.getName()));
        return "stellen/kompetenz-fragment :: kompetenz-eintrag";
    }

    @DeleteMapping("/{stelleId}/kompetenzen/{kompetenzId}")
    @HxRequest
    public ResponseEntity<String> kompetenzEntfernen(
            @PathVariable UUID stelleId, @PathVariable Integer kompetenzId) {
        stelleService.kompetenzEntfernen(stelleId, kompetenzId);
        return ResponseEntity.ok().body("");
    }

    private StelleService.StelleRequest buildRequest(
            String titel, String unternehmen, String beschreibung,
            String ortBezeichnung, double ortLat, double ortLon,
            Integer berufId, List<Integer> kompetenzIds, List<Boolean> pflichtFlags) {

        List<StelleService.KompetenzEintrag> kompetenzen = null;
        if (kompetenzIds != null) {
            kompetenzen = new java.util.ArrayList<>();
            for (int i = 0; i < kompetenzIds.size(); i++) {
                boolean pflicht = pflichtFlags != null && i < pflichtFlags.size()
                        ? pflichtFlags.get(i) : true;
                kompetenzen.add(new StelleService.KompetenzEintrag(kompetenzIds.get(i), pflicht));
            }
        }
        return new StelleService.StelleRequest(titel, unternehmen, beschreibung,
                ortBezeichnung, ortLat, ortLon, berufId, kompetenzen);
    }
}
